from __future__ import annotations

import json

import httpx
import pytest

from app.client import AgentTokenProvider, StaticTokenProvider, TradingOsClient, client_from_environment


def test_client_requires_agent_credential(monkeypatch, tmp_path):
    monkeypatch.delenv("TRADING_OS_AGENT_CREDENTIAL", raising=False)
    monkeypatch.setenv("TRADING_OS_AGENT_CREDENTIAL_FILE", str(tmp_path / "missing"))

    with pytest.raises(RuntimeError, match="TRADING_OS_AGENT_CREDENTIAL is required"):
        client_from_environment()


def test_client_from_environment_reuses_process_client(monkeypatch):
    monkeypatch.setenv("TRADING_OS_AGENT_CREDENTIAL", "opaque-credential")

    first = client_from_environment()
    second = client_from_environment()

    assert first is second


def test_client_uses_gateway_jwt(monkeypatch):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["authorization"] = request.headers["authorization"]
        captured["path"] = request.url.path
        return httpx.Response(200, json={"markets": []}, request=request)

    class Stream:
        def __enter__(self):
            self.response = httpx.MockTransport(handler).handle_request(
                httpx.Request(
                    "GET",
                    "http://127.0.0.1/api/v1/markets",
                    headers={"Authorization": "Bearer test-token"},
                )
            )
            return self.response

        def __exit__(self, *args):
            return False

    monkeypatch.setattr(httpx, "stream", lambda *args, **kwargs: Stream())

    result = TradingOsClient("http://127.0.0.1", StaticTokenProvider("test-token")).get("/api/v1/markets")

    assert json.loads(result) == {"markets": []}
    assert captured == {"authorization": "Bearer test-token", "path": "/api/v1/markets"}


def test_agent_token_provider_exchanges_and_caches_credential(monkeypatch):
    calls = []

    class Stream:
        def __enter__(self):
            calls.append((self.args, self.kwargs))
            return httpx.Response(
                200,
                json={"accessToken": "agent-token", "expiresIn": 300},
                request=httpx.Request("POST", "http://127.0.0.1"),
            )

        def __exit__(self, *args):
            return False

    def stream(*args, **kwargs):
        value = Stream()
        value.args = args
        value.kwargs = kwargs
        return value

    monkeypatch.setattr(httpx, "stream", stream)
    provider = AgentTokenProvider("http://127.0.0.1", "opaque-credential", 10, 1024)

    assert provider.token() == "agent-token"
    assert provider.token() == "agent-token"
    assert len(calls) == 1
    assert calls[0][1]["json"] == {"credential": "opaque-credential"}


def test_client_reauthenticates_once_after_unauthorized(monkeypatch):
    class Provider:
        def __init__(self):
            self.tokens = iter(["expired-token", "fresh-token"])
            self.invalidations = 0

        def token(self):
            return next(self.tokens)

        def invalidate(self):
            self.invalidations += 1

    provider = Provider()
    responses = iter([
        httpx.Response(401, content=b"{}", request=httpx.Request("GET", "http://127.0.0.1")),
        httpx.Response(200, content=b"{}", request=httpx.Request("GET", "http://127.0.0.1")),
    ])
    seen = []

    class Stream:
        def __enter__(self):
            seen.append(self.request_headers)
            return next(responses)

        def __exit__(self, *args):
            return False

    def stream(*args, **kwargs):
        stream_obj = Stream()
        stream_obj.request_headers = kwargs["headers"]
        return stream_obj

    monkeypatch.setattr(httpx, "stream", stream)
    assert TradingOsClient("http://127.0.0.1", provider).get("/api/v1/markets") == "{}"
    assert [headers["Authorization"] for headers in seen] == [
        "Bearer expired-token", "Bearer fresh-token"
    ]
    assert provider.invalidations == 1


def test_client_does_not_replay_post_after_unauthorized(monkeypatch):
    calls = []

    class Stream:
        def __enter__(self):
            calls.append(1)
            return httpx.Response(
                401, content=b"{}", request=httpx.Request("POST", "http://127.0.0.1")
            )

        def __exit__(self, *args):
            return False

    provider = StaticTokenProvider("agent-token")
    monkeypatch.setattr(httpx, "stream", lambda *args, **kwargs: Stream())

    with pytest.raises(RuntimeError, match="Gateway request failed with HTTP 401"):
        TradingOsClient("http://127.0.0.1", provider).post(
            "/api/v1/agent/broker-accounts/synchronize", {}, "sync-1"
        )

    assert len(calls) == 1


def test_client_rejects_plain_http_for_remote_gateway():
    client = TradingOsClient("http://gateway.internal", StaticTokenProvider("test-token"))

    try:
        client.get("/api/v1/markets")
    except RuntimeError as error:
        assert str(error) == "TRADING_OS_GATEWAY_URL must use HTTPS outside localhost"
    else:
        raise AssertionError("remote plain HTTP must be rejected")


def test_client_rejects_oversized_response(monkeypatch):
    class Stream:
        def __enter__(self):
            return httpx.Response(
                200,
                content=b"{}",
                request=httpx.Request("GET", "http://127.0.0.1"),
            )

        def __exit__(self, *args):
            return False

    monkeypatch.setattr(httpx, "stream", lambda *args, **kwargs: Stream())

    try:
        TradingOsClient("http://127.0.0.1", StaticTokenProvider("test-token"), max_response_bytes=1).get(
            "/api/v1/markets"
        )
    except RuntimeError as error:
        assert str(error) == "Gateway response exceeds the configured MCP response limit"
    else:
        raise AssertionError("oversized response must be rejected")


def test_client_rejects_oversized_post_response(monkeypatch):
    class Stream:
        def __enter__(self):
            return httpx.Response(
                200,
                content=b"{}",
                request=httpx.Request("POST", "http://127.0.0.1"),
            )

        def __exit__(self, *args):
            return False

    monkeypatch.setattr(httpx, "stream", lambda *args, **kwargs: Stream())

    with pytest.raises(RuntimeError, match="configured MCP response limit"):
        TradingOsClient("http://127.0.0.1", StaticTokenProvider("test-token"), max_response_bytes=1).post(
            "/api/v1/intelligence/research", {}, "research-key"
        )


def test_client_http_error_does_not_include_jwt(monkeypatch):
    request = httpx.Request("GET", "https://gateway.internal/api/v1/markets")
    response = httpx.Response(401, request=request, text="token must not leak")
    class Stream:
        def __enter__(self):
            return response

        def __exit__(self, *args):
            return False

    monkeypatch.setattr(httpx, "stream", lambda *args, **kwargs: Stream())

    try:
        TradingOsClient("https://gateway.internal", StaticTokenProvider("secret-jwt")).get("/api/v1/markets")
    except RuntimeError as error:
        assert "secret-jwt" not in str(error)
        assert str(error) == "Gateway request failed with HTTP 401"
    else:
        raise AssertionError("HTTP error must be raised")


def test_client_rejects_invalid_json(monkeypatch):
    class Stream:
        def __enter__(self):
            return httpx.Response(
                200,
                content=b"not-json",
                request=httpx.Request("GET", "http://127.0.0.1"),
            )

        def __exit__(self, *args):
            return False

    monkeypatch.setattr(httpx, "stream", lambda *args, **kwargs: Stream())

    try:
        TradingOsClient("http://127.0.0.1", StaticTokenProvider("test-token")).get("/api/v1/markets")
    except RuntimeError as error:
        assert str(error) == "Gateway returned invalid JSON"
    else:
        raise AssertionError("invalid JSON must be rejected")


@pytest.mark.parametrize("status_code", [403, 404, 500, 502])
def test_client_sanitizes_gateway_status_errors(monkeypatch, status_code):
    class Stream:
        def __enter__(self):
            return httpx.Response(
                status_code,
                content=b'{"secret":"must not escape"}',
                request=httpx.Request("GET", "http://127.0.0.1"),
            )

        def __exit__(self, *args):
            return False

    monkeypatch.setattr(httpx, "stream", lambda *args, **kwargs: Stream())

    with pytest.raises(RuntimeError, match=f"Gateway request failed with HTTP {status_code}") as error:
        TradingOsClient("http://127.0.0.1", StaticTokenProvider("test-token")).get("/api/v1/markets")

    assert "must not escape" not in str(error.value)


def test_client_sanitizes_timeout(monkeypatch):
    monkeypatch.setattr(httpx, "stream", lambda *args, **kwargs: (_ for _ in ()).throw(
        httpx.TimeoutException("secret timeout details")
    ))

    with pytest.raises(RuntimeError, match="Gateway request timed out"):
        TradingOsClient("http://127.0.0.1", StaticTokenProvider("test-token")).get("/api/v1/markets")


def test_client_sanitizes_network_failure(monkeypatch):
    monkeypatch.setattr(httpx, "stream", lambda *args, **kwargs: (_ for _ in ()).throw(
        httpx.ConnectError("secret network details")
    ))

    with pytest.raises(RuntimeError, match="Gateway is unavailable"):
        TradingOsClient("http://127.0.0.1", StaticTokenProvider("test-token")).get("/api/v1/markets")


def test_client_preserves_valid_json_text(monkeypatch):
    body = b'{"amount":1.2300,"label":"quoted value"}'

    class Stream:
        def __enter__(self):
            return httpx.Response(
                200,
                content=body,
                request=httpx.Request("GET", "http://127.0.0.1"),
            )

        def __exit__(self, *args):
            return False

    monkeypatch.setattr(httpx, "stream", lambda *args, **kwargs: Stream())

    assert TradingOsClient("http://127.0.0.1", StaticTokenProvider("test-token")).get("/api/v1/markets") == body.decode()
