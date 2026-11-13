from __future__ import annotations

import errno
import json
import os
import secrets
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

from app.client import (
    GatewayClientError,
    clear_runtime_credential,
    client_from_environment,
    load_runtime_credential,
    persist_runtime_credential,
    persist_authority_binding,
    set_runtime_credential,
    current_authority_binding,
    validate_authority_binding,
)
from app.server import mcp


class BridgeState:
    def __init__(self, ttl_seconds: float = 120.0, recover: bool = False) -> None:
        self._ttl_seconds = ttl_seconds
        self._lock = threading.Lock()
        self._challenges: dict[str, float] = {}
        self._connected = False
        self._last_recovery_error: GatewayClientError | None = None
        if recover:
            self._recover_existing_credential()

    def _recover_existing_credential(self, *, raise_errors: bool = False) -> None:
        try:
            credential = load_runtime_credential()
            if not credential:
                raise GatewayClientError(
                    "No persisted MCP credential is available",
                    code="CREDENTIAL_MISSING",
                    status=401,
                    next_action="Use explicit human Connect or lost-credential recovery.",
                )
            binding_exists = validate_authority_binding()
            set_runtime_credential(credential)
            client_from_environment().token_provider.token()
            if not binding_exists:
                persist_authority_binding(current_authority_binding())
        except GatewayClientError as error:
            clear_runtime_credential()
            self._last_recovery_error = error
            if raise_errors:
                raise
            return
        except (OSError, RuntimeError) as error:
            clear_runtime_credential()
            self._last_recovery_error = GatewayClientError(
                "MCP authority configuration is invalid",
                code="CONFIGURATION_INVALID",
                status=400,
                next_action="Correct the bridge authority configuration and use explicit recovery.",
            )
            if raise_errors:
                raise self._last_recovery_error from error
            return
        with self._lock:
            self._connected = True
            self._last_recovery_error = None

    def reconnect(self) -> None:
        self._recover_existing_credential(raise_errors=True)

    def challenge(self) -> str:
        value = secrets.token_urlsafe(32)
        with self._lock:
            self._purge()
            self._challenges[value] = time.monotonic() + self._ttl_seconds
        return value

    def connect(self, challenge: str, credential: str) -> None:
        with self._lock:
            self._purge()
            expires_at = self._challenges.pop(challenge, None)
            if expires_at is None or expires_at <= time.monotonic():
                raise ValueError("pairing challenge is invalid or expired")
        set_runtime_credential(credential)
        try:
            client_from_environment().token_provider.token()
        except GatewayClientError:
            clear_runtime_credential()
            raise
        try:
            persist_runtime_credential(credential)
            persist_authority_binding(current_authority_binding())
        except (OSError, RuntimeError) as error:
            clear_runtime_credential()
            raise GatewayClientError(
                "MCP credential handoff failed",
                code="CONFIGURATION_INVALID",
                status=500,
                next_action="Check the owner-only bridge credential store and retry explicit recovery.",
            ) from error
        with self._lock:
            self._connected = True
            self._last_recovery_error = None

    def connected(self) -> bool:
        with self._lock:
            return self._connected

    def _purge(self) -> None:
        now = time.monotonic()
        self._challenges = {key: value for key, value in self._challenges.items() if value > now}


class BridgeHandler(BaseHTTPRequestHandler):
    state: BridgeState
    allowed_origins: set[str]

    def do_OPTIONS(self) -> None:
        if not self._origin_allowed():
            self.send_error(HTTPStatus.FORBIDDEN)
            return
        self.send_response(HTTPStatus.NO_CONTENT)
        self._cors_headers()
        self.end_headers()

    def do_GET(self) -> None:
        if not self._origin_allowed():
            self.send_error(HTTPStatus.FORBIDDEN)
            return
        path = urlparse(self.path).path
        if path == "/v1/challenge":
            self._json(HTTPStatus.OK, {"challenge": self.state.challenge()})
            return
        if path == "/v1/status":
            self._json(HTTPStatus.OK, {"connected": self.state.connected()})
            return
        self.send_error(HTTPStatus.NOT_FOUND)

    def do_POST(self) -> None:
        if not self._origin_allowed():
            self.send_error(HTTPStatus.FORBIDDEN)
            return
        if urlparse(self.path).path != "/v1/connect":
            if urlparse(self.path).path == "/v1/reconnect":
                try:
                    self.state.reconnect()
                except GatewayClientError as error:
                    self._json(self._status_for(error), error.as_dict())
                    return
                self.send_response(HTTPStatus.NO_CONTENT)
                self._cors_headers()
                self.end_headers()
                return
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 8192:
                raise ValueError("request body is invalid")
            payload = json.loads(self.rfile.read(length))
            challenge = payload.get("challenge")
            credential = payload.get("credential")
            if not isinstance(challenge, str) or not isinstance(credential, str):
                raise ValueError("pairing request is invalid")
            self.state.connect(challenge, credential)
        except (ValueError, GatewayClientError, json.JSONDecodeError):
            self._json(HTTPStatus.BAD_REQUEST, {"error": "pairing failed"})
            return
        self.send_response(HTTPStatus.NO_CONTENT)
        self._cors_headers()
        self.end_headers()

    def log_message(self, format: str, *args: object) -> None:
        return

    def _origin_allowed(self) -> bool:
        origin = self.headers.get("Origin")
        return origin in self.allowed_origins

    def _cors_headers(self) -> None:
        origin = self.headers.get("Origin")
        self.send_header("Access-Control-Allow-Origin", origin or "")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Cache-Control", "no-store")

    def _json(self, status: HTTPStatus, payload: dict[str, object]) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self._cors_headers()
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _status_for(self, error: GatewayClientError) -> HTTPStatus:
        if error.code == "ENVIRONMENT_MISMATCH":
            return HTTPStatus.CONFLICT
        if error.code in {"CREDENTIAL_MISSING", "AUTHENTICATION_FAILED"}:
            return HTTPStatus.UNAUTHORIZED
        if error.code == "PERMISSION_DENIED":
            return HTTPStatus.FORBIDDEN
        if error.code == "UPSTREAM_UNAVAILABLE":
            return HTTPStatus.SERVICE_UNAVAILABLE
        return HTTPStatus.BAD_REQUEST


def main() -> None:
    host = "127.0.0.1"
    port = int(os.environ.get("TRADING_OS_MCP_BRIDGE_PORT", "17891"))
    origins = {
        origin.strip()
        for origin in os.environ.get(
            "TRADING_OS_MCP_BRIDGE_ALLOWED_ORIGINS",
            "http://localhost:4200,http://127.0.0.1:4200,http://localhost:17085,http://127.0.0.1:17085",
        ).split(",")
        if origin.strip()
    }
    state = BridgeState(recover=True)
    handler = type("ConfiguredBridgeHandler", (BridgeHandler,), {})
    handler.state = state
    handler.allowed_origins = origins
    try:
        http_server = ThreadingHTTPServer((host, port), handler)
    except OSError as error:
        if error.errno != errno.EADDRINUSE:
            raise
    else:
        threading.Thread(target=http_server.serve_forever, name="mcp-bridge-http", daemon=True).start()
    mcp.run()


if __name__ == "__main__":
    main()
