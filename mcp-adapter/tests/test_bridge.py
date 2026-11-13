from __future__ import annotations

import json
import threading
from http.client import HTTPConnection

import pytest

from app.bridge import BridgeHandler, BridgeState
from app.client import load_runtime_credential


@pytest.fixture
def bridge_server():
    from http.server import ThreadingHTTPServer

    handler = type("TestBridgeHandler", (BridgeHandler,), {})
    handler.state = BridgeState()
    handler.allowed_origins = {"http://localhost:4200"}
    server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield server
    server.shutdown()
    thread.join()


def request(server, method, path, payload=None):
    connection = HTTPConnection("127.0.0.1", server.server_port)
    body = json.dumps(payload).encode() if payload is not None else None
    headers = {"Origin": "http://localhost:4200"}
    if body is not None:
        headers["Content-Type"] = "application/json"
        headers["Content-Length"] = str(len(body))
    connection.request(method, path, body=body, headers=headers)
    response = connection.getresponse()
    content = response.read()
    connection.close()
    return response.status, json.loads(content) if content else None


def test_challenge_is_single_use(bridge_server, monkeypatch, tmp_path):
    monkeypatch.setenv("TRADING_OS_AGENT_CREDENTIAL_FILE", str(tmp_path / "credential"))
    monkeypatch.setenv("TRADING_OS_AGENT_AUTHORITY_FILE", str(tmp_path / "binding.json"))
    monkeypatch.setattr("app.bridge.client_from_environment", lambda: type(
        "Client", (), {"token_provider": type("Provider", (), {"token": lambda self: "token"})()}
    )())
    status, payload = request(bridge_server, "GET", "/v1/challenge")
    assert status == 200

    connect_payload = {"challenge": payload["challenge"], "credential": "opaque-credential"}
    assert request(bridge_server, "POST", "/v1/connect", connect_payload)[0] == 204
    assert request(bridge_server, "POST", "/v1/connect", connect_payload)[0] == 400


def test_bridge_rejects_untrusted_origin(bridge_server):
    from http.client import HTTPConnection

    connection = HTTPConnection("127.0.0.1", bridge_server.server_port)
    connection.request("GET", "/v1/challenge", headers={"Origin": "https://evil.example"})
    response = connection.getresponse()
    assert response.status == 403


def test_successful_pairing_persists_credential_for_fresh_process(bridge_server, monkeypatch, tmp_path):
    credential_file = tmp_path / "mcp-agent-credential"
    monkeypatch.setenv("TRADING_OS_AGENT_CREDENTIAL_FILE", str(credential_file))
    monkeypatch.setenv("TRADING_OS_AGENT_AUTHORITY_FILE", str(tmp_path / "binding.json"))
    monkeypatch.setattr("app.bridge.client_from_environment", lambda: type(
        "Client", (), {"token_provider": type("Provider", (), {"token": lambda self: "token"})()}
    )())
    status, payload = request(bridge_server, "GET", "/v1/challenge")
    assert status == 200

    assert request(
        bridge_server,
        "POST",
        "/v1/connect",
        {"challenge": payload["challenge"], "credential": "opaque-credential"},
    )[0] == 204
    assert load_runtime_credential() == "opaque-credential"
    assert credential_file.stat().st_mode & 0o777 == 0o600
    assert (tmp_path / "binding.json").stat().st_mode & 0o777 == 0o600


def test_bridge_recovers_persisted_credential_on_startup(monkeypatch, tmp_path):
    credential_file = tmp_path / "mcp-agent-credential"
    credential_file.write_text("opaque-credential", encoding="utf-8")
    credential_file.chmod(0o600)
    monkeypatch.setenv("TRADING_OS_AGENT_CREDENTIAL_FILE", str(credential_file))
    monkeypatch.setenv("TRADING_OS_AGENT_AUTHORITY_FILE", str(tmp_path / "binding.json"))
    monkeypatch.setattr("app.bridge.client_from_environment", lambda: type(
        "Client", (), {"token_provider": type("Provider", (), {"token": lambda self: "token"})()}
    )())

    state = BridgeState(recover=True)

    assert state.connected() is True
    assert (tmp_path / "binding.json").exists()


def test_bridge_reconnect_does_not_issue_or_rotate_credential(monkeypatch, tmp_path):
    credential_file = tmp_path / "mcp-agent-credential"
    credential_file.write_text("opaque-credential", encoding="utf-8")
    credential_file.chmod(0o600)
    monkeypatch.setenv("TRADING_OS_AGENT_CREDENTIAL_FILE", str(credential_file))
    monkeypatch.setenv("TRADING_OS_AGENT_AUTHORITY_FILE", str(tmp_path / "binding.json"))
    monkeypatch.setattr("app.bridge.client_from_environment", lambda: type(
        "Client", (), {"token_provider": type("Provider", (), {"token": lambda self: "token"})()}
    )())

    state = BridgeState()
    state.reconnect()

    assert state.connected() is True
    assert load_runtime_credential() == "opaque-credential"


def test_bridge_does_not_mark_invalid_persisted_credential_connected(monkeypatch, tmp_path):
    credential_file = tmp_path / "mcp-agent-credential"
    credential_file.write_text("invalid-credential", encoding="utf-8")
    credential_file.chmod(0o600)
    monkeypatch.setenv("TRADING_OS_AGENT_CREDENTIAL_FILE", str(credential_file))
    monkeypatch.setenv("TRADING_OS_AGENT_AUTHORITY_FILE", str(tmp_path / "binding.json"))

    def reject():
        from app.client import GatewayClientError
        raise GatewayClientError("Agent credential exchange failed", code="AUTHENTICATION_FAILED", status=401)

    monkeypatch.setattr("app.bridge.client_from_environment", lambda: type(
        "Client", (), {"token_provider": type("Provider", (), {"token": lambda self: reject()})()}
    )())

    state = BridgeState(recover=True)

    assert state.connected() is False


def test_bridge_reconnect_reports_environment_mismatch(monkeypatch, tmp_path):
    credential_file = tmp_path / "mcp-agent-credential"
    credential_file.write_text("opaque-credential", encoding="utf-8")
    credential_file.chmod(0o600)
    binding_file = tmp_path / "binding.json"
    binding_file.write_text(
        '{"gatewayAuthority":"https://other.example","jwtAudience":null,"jwtIssuer":null,"schemaVersion":1}\n',
        encoding="utf-8",
    )
    binding_file.chmod(0o600)
    monkeypatch.setenv("TRADING_OS_AGENT_CREDENTIAL_FILE", str(credential_file))
    monkeypatch.setenv("TRADING_OS_AGENT_AUTHORITY_FILE", str(binding_file))
    monkeypatch.setenv("TRADING_OS_GATEWAY_URL", "https://gateway.example")

    state = BridgeState()

    with pytest.raises(Exception) as error:
        state.reconnect()

    assert error.value.code == "ENVIRONMENT_MISMATCH"


def test_reconnect_endpoint_reports_missing_credential_without_secret(monkeypatch, tmp_path):
    monkeypatch.setenv("TRADING_OS_AGENT_CREDENTIAL_FILE", str(tmp_path / "missing"))
    monkeypatch.setenv("TRADING_OS_AGENT_AUTHORITY_FILE", str(tmp_path / "binding.json"))
    handler = type("MissingCredentialBridgeHandler", (BridgeHandler,), {})
    handler.state = BridgeState()
    handler.allowed_origins = {"http://localhost:4200"}
    from http.server import ThreadingHTTPServer

    server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        status, payload = request(server, "POST", "/v1/reconnect", {})
    finally:
        server.shutdown()
        thread.join()

    assert status == 401
    assert payload["error"]["code"] == "CREDENTIAL_MISSING"
    assert "opaque-credential" not in json.dumps(payload["error"])
