from __future__ import annotations

import json
import os
import pathlib
import tempfile
import threading
import time
import uuid
from dataclasses import dataclass
from typing import Any, Protocol
from urllib.parse import urlparse

import httpx


class GatewayClientError(RuntimeError):
    """Sanitized, structured error exposed to the MCP tool boundary."""

    def __init__(self, message: str, *, code: str = "INTERNAL_ERROR", status: int | None = None,
                 correlation_id: str | None = None, retryable: bool = False,
                 next_action: str = "Inspect the error and retry only when advised."):
        super().__init__(message)
        self.code = code
        self.status = status
        self.correlation_id = correlation_id
        self.retryable = retryable
        self.next_action = next_action

    def as_dict(self) -> dict[str, object]:
        return {
            "error": {
                "code": self.code,
                "message": str(self),
                "status": self.status,
                "correlationId": self.correlation_id,
                "retryable": self.retryable,
                "nextAction": self.next_action,
            }
        }


@dataclass(frozen=True)
class AuthorityBinding:
    """Non-secret identity of the Gateway authority trusted by this bridge."""

    schema_version: int
    gateway_authority: str
    jwt_issuer: str | None
    jwt_audience: str | None


class AccessTokenProvider(Protocol):
    def token(self) -> str:
        ...

    def invalidate(self) -> None:
        ...


class AgentTokenProvider:
    def __init__(self, base_url: str, credential: str, timeout_seconds: float,
                 max_response_bytes: int):
        self._base_url = base_url
        self._credential = credential
        self._timeout_seconds = timeout_seconds
        self._max_response_bytes = max_response_bytes
        self._access_token: str | None = None
        self._expires_at = 0.0
        self._exchange_lock = threading.Lock()

    def token(self) -> str:
        if self._access_token is not None and time.monotonic() < self._expires_at:
            return self._access_token
        return self._exchange()

    def invalidate(self) -> None:
        self._access_token = None
        self._expires_at = 0.0

    def _exchange(self) -> str:
        with self._exchange_lock:
            if self._access_token is not None and time.monotonic() < self._expires_at:
                return self._access_token
            correlation_id = str(uuid.uuid4())
            try:
                with httpx.stream(
                    "POST",
                    f"{self._base_url.rstrip('/')}/api/v1/agents/token",
                    json={"credential": self._credential},
                    headers={"X-Correlation-ID": correlation_id},
                    timeout=self._timeout_seconds,
                ) as response:
                    body = bytearray()
                    for chunk in response.iter_bytes():
                        body.extend(chunk)
                        if len(body) > self._max_response_bytes:
                            raise GatewayClientError(
                                "Gateway token response exceeds the configured MCP response limit",
                                code="UPSTREAM_UNAVAILABLE", status=response.status_code,
                                correlation_id=correlation_id,
                                next_action="Retry after the Gateway returns a bounded token response.",
                            )
                    if response.status_code >= 400:
                        code = "AUTHENTICATION_FAILED" if response.status_code == 401 else (
                            "PERMISSION_DENIED" if response.status_code == 403 else "UPSTREAM_UNAVAILABLE"
                        )
                        raise GatewayClientError("Agent credential exchange failed", code=code,
                                                 status=response.status_code, correlation_id=correlation_id,
                                                 retryable=response.status_code >= 500,
                                                 next_action="Rotate the credential only when it is invalid or revoked.")
                    try:
                        payload = json.loads(bytes(body).decode(response.encoding or "utf-8"))
                    except (UnicodeDecodeError, json.JSONDecodeError) as error:
                        raise GatewayClientError("Gateway returned an invalid agent token response",
                                                  code="INTERNAL_ERROR", status=response.status_code,
                                                  correlation_id=correlation_id) from error
                token = payload.get("accessToken")
                expires_in = payload.get("expiresIn")
                if not isinstance(token, str) or not token or not isinstance(expires_in, (int, float)):
                    raise GatewayClientError("Gateway returned an invalid agent token response",
                                              code="INTERNAL_ERROR", status=200,
                                              correlation_id=correlation_id)
                self._access_token = token
                self._expires_at = time.monotonic() + max(1.0, float(expires_in) - 30.0)
                return token
            except GatewayClientError:
                raise
            except (httpx.TimeoutException, httpx.RequestError) as error:
                raise GatewayClientError("Gateway is unavailable", code="UPSTREAM_UNAVAILABLE",
                                         correlation_id=correlation_id, retryable=True,
                                         next_action="Retry after the Gateway is reachable.") from error


class StaticTokenProvider:
    def __init__(self, value: str):
        self._value = value

    def token(self) -> str:
        return self._value

    def invalidate(self) -> None:
        return None


@dataclass(frozen=True)
class TradingOsClient:
    base_url: str
    token_provider: AccessTokenProvider
    timeout_seconds: float = 10.0
    max_response_bytes: int = 262_144

    def get(self, path: str, params: dict[str, Any] | None = None) -> str:
        return self._request("GET", path, params=params, retry_on_unauthorized=True)

    def post(self, path: str, payload: dict[str, Any], idempotency_key: str) -> str:
        return self._request("POST", path, payload=payload, idempotency_key=idempotency_key,
                             retry_on_unauthorized=False)

    def _request(self, method: str, path: str, params: dict[str, Any] | None = None,
                 payload: dict[str, Any] | None = None, idempotency_key: str | None = None,
                 retry_on_unauthorized: bool = False) -> str:
        parsed = urlparse(self.base_url)
        if parsed.hostname not in {"127.0.0.1", "localhost", "::1"} and parsed.scheme != "https":
            raise GatewayClientError("TRADING_OS_GATEWAY_URL must use HTTPS outside localhost")
        attempts = 2 if retry_on_unauthorized else 1
        correlation_id = str(uuid.uuid4())
        for attempt in range(attempts):
            try:
                with httpx.stream(
                    method,
                    f"{self.base_url.rstrip('/')}{path}",
                    params=params,
                    json=payload,
                    headers={
                        "Authorization": f"Bearer {self.token_provider.token()}",
                        "X-Correlation-ID": correlation_id,
                        **({"Idempotency-Key": idempotency_key} if idempotency_key else {}),
                    },
                    timeout=self.timeout_seconds,
                ) as response:
                    if response.status_code == 401 and retry_on_unauthorized and attempt == 0:
                        self.token_provider.invalidate()
                        continue
                    if response.status_code == 401:
                        self.token_provider.invalidate()
                    body = self._read_body(response, "Gateway response exceeds the configured MCP response limit")
                    if response.status_code >= 400:
                        raise self._http_error(response, body, correlation_id)
                    try:
                        text = bytes(body).decode(response.encoding or "utf-8")
                        json.loads(text)
                    except (UnicodeDecodeError, json.JSONDecodeError) as error:
                        raise GatewayClientError("Gateway returned invalid JSON", code="INTERNAL_ERROR",
                                                  status=response.status_code, correlation_id=correlation_id) from error
                    return text
            except GatewayClientError:
                raise
            except httpx.TimeoutException as error:
                raise GatewayClientError("Gateway request timed out", code="UPSTREAM_UNAVAILABLE",
                                         correlation_id=correlation_id, retryable=True,
                                         next_action="Retry after the Gateway is reachable.") from error
            except httpx.RequestError as error:
                raise GatewayClientError("Gateway is unavailable", code="UPSTREAM_UNAVAILABLE",
                                         correlation_id=correlation_id, retryable=True,
                                         next_action="Retry after the Gateway is reachable.") from error
        raise GatewayClientError("Agent access token expired", code="TOKEN_EXPIRED", status=401,
                                 correlation_id=correlation_id, retryable=False,
                                 next_action="Reconnect only if the underlying agent credential is invalid or revoked.")

    def _read_body(self, response: httpx.Response, limit_message: str) -> bytearray:
        body = bytearray()
        for chunk in response.iter_bytes():
            body.extend(chunk)
            if len(body) > self.max_response_bytes:
                raise GatewayClientError(limit_message, code="MARKET_DATA_UNAVAILABLE",
                                         status=response.status_code,
                                         correlation_id=response.headers.get("X-Correlation-ID"),
                                         next_action="Use a bounded instrument filter or pagination.")
        return body

    def _http_error(self, response: httpx.Response, body: bytearray,
                    correlation_id: str) -> GatewayClientError:
        safe_code = {
            400: "INVALID_REQUEST",
            401: "AUTHENTICATION_FAILED",
            403: "PERMISSION_DENIED",
            404: "ACCOUNT_ACCESS_DENIED",
            409: "ACCOUNT_FACTS_INCOMPLETE",
            502: "UPSTREAM_UNAVAILABLE",
            503: "UPSTREAM_UNAVAILABLE",
            504: "UPSTREAM_UNAVAILABLE",
        }.get(response.status_code, "INTERNAL_ERROR")
        details: dict[str, Any] = {}
        try:
            parsed = json.loads(bytes(body).decode(response.encoding or "utf-8"))
            if isinstance(parsed, dict):
                details = parsed
        except (UnicodeDecodeError, json.JSONDecodeError):
            pass
        code = details.get("code") if isinstance(details.get("code"), str) else safe_code
        message = details.get("detail") if isinstance(details.get("detail"), str) else "Gateway request failed"
        response_correlation = response.headers.get("X-Correlation-ID") or response.headers.get("X-Request-ID")
        return GatewayClientError(message, code=code, status=response.status_code,
                                   correlation_id=response_correlation or correlation_id,
                                   retryable=response.status_code in {502, 503, 504},
                                   next_action="Retry only when the error is marked retryable.")


_cached_client: TradingOsClient | None = None
_cached_configuration: tuple[str, str, float, int] | None = None


def set_runtime_credential(credential: str) -> None:
    """Replace the runtime credential received through the pairing bridge."""
    if not credential or len(credential) > 512:
        raise ValueError("credential is invalid")
    global _cached_client, _cached_configuration
    os.environ["TRADING_OS_AGENT_CREDENTIAL"] = credential
    _cached_client = None
    _cached_configuration = None


def persist_runtime_credential(credential: str) -> None:
    """Persist only the opaque agent credential for fresh local bridge processes."""
    if not credential or len(credential) > 512:
        raise ValueError("credential is invalid")
    path = credential_store_path()
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(path.parent, 0o700)
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            descriptor = -1
            stream.write(credential)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_name, path)
        os.chmod(path, 0o600)
    except BaseException:
        if descriptor >= 0:
            os.close(descriptor)
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass
        raise


def credential_store_path() -> pathlib.Path:
    configured = os.environ.get("TRADING_OS_AGENT_CREDENTIAL_FILE")
    return pathlib.Path(configured).expanduser() if configured else (
        pathlib.Path.home() / ".config" / "trading-os" / "mcp-agent-credential"
    )


def authority_binding_path() -> pathlib.Path:
    configured = os.environ.get("TRADING_OS_AGENT_AUTHORITY_FILE")
    if configured:
        return pathlib.Path(configured).expanduser()
    credential_path = credential_store_path()
    return credential_path.with_name(f"{credential_path.name}.binding.json")


def normalize_gateway_authority(base_url: str) -> str:
    parsed = urlparse(base_url)
    if parsed.username or parsed.password or not parsed.scheme or not parsed.hostname:
        raise RuntimeError("TRADING_OS_GATEWAY_URL is invalid")
    hostname = parsed.hostname.lower()
    if ":" in hostname:
        hostname = f"[{hostname}]"
    port = f":{parsed.port}" if parsed.port is not None else ""
    path = parsed.path.rstrip("/")
    return f"{parsed.scheme.lower()}://{hostname}{port}{path}"


def current_authority_binding(base_url: str | None = None) -> AuthorityBinding:
    authority = normalize_gateway_authority(
        base_url or os.environ.get("TRADING_OS_GATEWAY_URL", "http://127.0.0.1:17080")
    )
    issuer = os.environ.get("TRADING_OS_AGENT_JWT_ISSUER", "").strip() or None
    audience = os.environ.get("TRADING_OS_AGENT_JWT_AUDIENCE", "").strip() or None
    return AuthorityBinding(1, authority, issuer, audience)


def load_authority_binding() -> AuthorityBinding | None:
    path = authority_binding_path()
    try:
        if path.stat().st_mode & 0o077:
            raise RuntimeError("MCP authority binding permissions are too broad")
        payload = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return None
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RuntimeError("MCP authority binding is invalid") from error
    if not isinstance(payload, dict):
        raise RuntimeError("MCP authority binding is invalid")
    issuer = payload.get("jwtIssuer")
    audience = payload.get("jwtAudience")
    if (payload.get("schemaVersion") != 1 or not isinstance(payload.get("gatewayAuthority"), str)
            or (issuer is not None and not isinstance(issuer, str))
            or (audience is not None and not isinstance(audience, str))):
        raise RuntimeError("MCP authority binding is invalid")
    return AuthorityBinding(1, payload["gatewayAuthority"], issuer, audience)


def persist_authority_binding(binding: AuthorityBinding) -> None:
    path = authority_binding_path()
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(path.parent, 0o700)
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            descriptor = -1
            json.dump({
                "schemaVersion": binding.schema_version,
                "gatewayAuthority": binding.gateway_authority,
                "jwtIssuer": binding.jwt_issuer,
                "jwtAudience": binding.jwt_audience,
            }, stream, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_name, path)
        os.chmod(path, 0o600)
    except BaseException:
        if descriptor >= 0:
            os.close(descriptor)
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass
        raise


def validate_authority_binding(base_url: str | None = None) -> bool:
    stored = load_authority_binding()
    if stored is None:
        return False
    if stored != current_authority_binding(base_url):
        raise GatewayClientError(
            "MCP credential belongs to a different Trading OS authentication authority",
            code="ENVIRONMENT_MISMATCH",
            status=409,
            retryable=False,
            next_action="Select the intended Gateway authority and use explicit human recovery.",
        )
    return True


def load_runtime_credential() -> str | None:
    path = credential_store_path()
    try:
        if path.stat().st_mode & 0o077:
            raise RuntimeError("MCP credential store permissions are too broad")
        value = path.read_text(encoding="utf-8").strip()
    except FileNotFoundError:
        return None
    if not value or len(value) > 512:
        raise RuntimeError("MCP credential store is invalid")
    return value


def clear_runtime_credential() -> None:
    global _cached_client, _cached_configuration
    os.environ.pop("TRADING_OS_AGENT_CREDENTIAL", None)
    _cached_client = None
    _cached_configuration = None


def client_from_environment() -> TradingOsClient:
    global _cached_client, _cached_configuration
    base_url = os.environ.get("TRADING_OS_GATEWAY_URL", "http://127.0.0.1:17080")
    credential = os.environ.get("TRADING_OS_AGENT_CREDENTIAL") or load_runtime_credential()
    if not credential:
        raise RuntimeError("TRADING_OS_AGENT_CREDENTIAL is required")
    timeout = float(os.environ.get("TRADING_OS_HTTP_TIMEOUT_SECONDS", "10"))
    max_response_bytes = int(os.environ.get("TRADING_OS_MAX_RESPONSE_BYTES", "262144"))
    if timeout <= 0 or max_response_bytes <= 0:
        raise RuntimeError("Gateway timeout and response limit must be positive")
    configuration = (base_url, credential, timeout, max_response_bytes)
    if _cached_client is not None and _cached_configuration == configuration:
        return _cached_client
    _cached_client = TradingOsClient(
        base_url,
        AgentTokenProvider(base_url, credential, timeout, max_response_bytes),
        timeout,
        max_response_bytes,
    )
    _cached_configuration = configuration
    return _cached_client
