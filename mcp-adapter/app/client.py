from __future__ import annotations

import json
import os
import time
from dataclasses import dataclass
from typing import Any, Protocol
from urllib.parse import urlparse

import httpx


class GatewayClientError(RuntimeError):
    """Sanitized error exposed to the MCP tool boundary."""


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

    def token(self) -> str:
        if self._access_token is not None and time.monotonic() < self._expires_at:
            return self._access_token
        return self._exchange()

    def invalidate(self) -> None:
        self._access_token = None
        self._expires_at = 0.0

    def _exchange(self) -> str:
        try:
            with httpx.stream(
                "POST",
                f"{self._base_url.rstrip('/')}/api/v1/agents/token",
                json={"credential": self._credential},
                timeout=self._timeout_seconds,
            ) as response:
                body = bytearray()
                for chunk in response.iter_bytes():
                    body.extend(chunk)
                    if len(body) > self._max_response_bytes:
                        raise GatewayClientError(
                            "Gateway token response exceeds the configured MCP response limit"
                        )
                response.raise_for_status()
                payload = json.loads(bytes(body).decode(response.encoding or "utf-8"))
            token = payload.get("accessToken")
            expires_in = payload.get("expiresIn")
            if not isinstance(token, str) or not token or not isinstance(expires_in, (int, float)):
                raise GatewayClientError("Gateway returned an invalid agent token response")
            self._access_token = token
            self._expires_at = time.monotonic() + max(1.0, float(expires_in) - 30.0)
            return token
        except GatewayClientError:
            raise
        except httpx.HTTPStatusError as error:
            raise GatewayClientError("Agent credential exchange failed") from error
        except (httpx.TimeoutException, httpx.RequestError) as error:
            raise GatewayClientError("Gateway is unavailable") from error
        except (UnicodeDecodeError, json.JSONDecodeError, TypeError, KeyError) as error:
            raise GatewayClientError("Gateway returned an invalid agent token response") from error


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
        for attempt in range(attempts):
            try:
                with httpx.stream(
                    method,
                    f"{self.base_url.rstrip('/')}{path}",
                    params=params,
                    json=payload,
                    headers={
                        "Authorization": f"Bearer {self.token_provider.token()}",
                        **({"Idempotency-Key": idempotency_key} if idempotency_key else {}),
                    },
                    timeout=self.timeout_seconds,
                ) as response:
                    if response.status_code == 401 and retry_on_unauthorized and attempt == 0:
                        self.token_provider.invalidate()
                        continue
                    if response.status_code == 401:
                        self.token_provider.invalidate()
                    body = bytearray()
                    for chunk in response.iter_bytes():
                        body.extend(chunk)
                        if len(body) > self.max_response_bytes:
                            raise GatewayClientError(
                                "Gateway response exceeds the configured MCP response limit"
                            )
                    try:
                        response.raise_for_status()
                    except httpx.HTTPStatusError as error:
                        raise GatewayClientError(
                            f"Gateway request failed with HTTP {response.status_code}"
                        ) from error
                    try:
                        text = bytes(body).decode(response.encoding or "utf-8")
                        json.loads(text)
                    except (UnicodeDecodeError, json.JSONDecodeError) as error:
                        raise GatewayClientError("Gateway returned invalid JSON") from error
                    return text
            except GatewayClientError:
                raise
            except httpx.HTTPStatusError as error:
                raise GatewayClientError(
                    f"Gateway request failed with HTTP {error.response.status_code}"
                ) from error
            except httpx.TimeoutException as error:
                raise GatewayClientError("Gateway request timed out") from error
            except httpx.RequestError as error:
                raise GatewayClientError("Gateway is unavailable") from error
        raise GatewayClientError("Gateway request failed with HTTP 401")


_cached_client: TradingOsClient | None = None
_cached_configuration: tuple[str, str, float, int] | None = None


def set_runtime_credential(credential: str) -> None:
    """Replace the process-local credential received through the pairing bridge."""
    if not credential or len(credential) > 512:
        raise ValueError("credential is invalid")
    global _cached_client, _cached_configuration
    os.environ["TRADING_OS_AGENT_CREDENTIAL"] = credential
    _cached_client = None
    _cached_configuration = None


def clear_runtime_credential() -> None:
    global _cached_client, _cached_configuration
    os.environ.pop("TRADING_OS_AGENT_CREDENTIAL", None)
    _cached_client = None
    _cached_configuration = None


def client_from_environment() -> TradingOsClient:
    global _cached_client, _cached_configuration
    base_url = os.environ.get("TRADING_OS_GATEWAY_URL", "http://127.0.0.1:17080")
    credential = os.environ.get("TRADING_OS_AGENT_CREDENTIAL")
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
