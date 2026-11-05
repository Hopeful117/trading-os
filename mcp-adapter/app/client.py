from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlparse

import httpx


class GatewayClientError(RuntimeError):
    """Sanitized error exposed to the MCP tool boundary."""


@dataclass(frozen=True)
class TradingOsClient:
    base_url: str
    jwt: str
    timeout_seconds: float = 10.0
    max_response_bytes: int = 262_144

    def get(self, path: str, params: dict[str, Any] | None = None) -> str:
        parsed = urlparse(self.base_url)
        if parsed.hostname not in {"127.0.0.1", "localhost", "::1"} and parsed.scheme != "https":
            raise GatewayClientError("TRADING_OS_GATEWAY_URL must use HTTPS outside localhost")
        try:
            with httpx.stream(
                "GET",
                f"{self.base_url.rstrip('/')}{path}",
                params=params,
                headers={"Authorization": f"Bearer {self.jwt}"},
                timeout=self.timeout_seconds,
            ) as response:
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
        except httpx.TimeoutException as error:
            raise GatewayClientError("Gateway request timed out") from error
        except httpx.RequestError as error:
            raise GatewayClientError("Gateway is unavailable") from error

    def post(self, path: str, payload: dict[str, Any], idempotency_key: str) -> str:
        parsed = urlparse(self.base_url)
        if parsed.hostname not in {"127.0.0.1", "localhost", "::1"} and parsed.scheme != "https":
            raise GatewayClientError("TRADING_OS_GATEWAY_URL must use HTTPS outside localhost")
        try:
            response = httpx.post(
                f"{self.base_url.rstrip('/')}{path}",
                json=payload,
                headers={
                    "Authorization": f"Bearer {self.jwt}",
                    "Idempotency-Key": idempotency_key,
                },
                timeout=self.timeout_seconds,
            )
            response.raise_for_status()
            if len(response.content) > self.max_response_bytes:
                raise GatewayClientError("Gateway response exceeds the configured MCP response limit")
            try:
                text = response.content.decode(response.encoding or "utf-8")
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


def client_from_environment() -> TradingOsClient:
    base_url = os.environ.get("TRADING_OS_GATEWAY_URL", "http://127.0.0.1:17080")
    jwt = os.environ.get("TRADING_OS_JWT")
    if not jwt:
        raise RuntimeError("TRADING_OS_JWT is required")
    timeout = float(os.environ.get("TRADING_OS_HTTP_TIMEOUT_SECONDS", "10"))
    max_response_bytes = int(os.environ.get("TRADING_OS_MAX_RESPONSE_BYTES", "262144"))
    if timeout <= 0 or max_response_bytes <= 0:
        raise RuntimeError("Gateway timeout and response limit must be positive")
    return TradingOsClient(base_url, jwt, timeout, max_response_bytes)
