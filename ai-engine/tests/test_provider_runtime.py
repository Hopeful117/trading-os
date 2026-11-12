from base64 import urlsafe_b64encode
from datetime import datetime, timedelta, timezone
from pathlib import Path
from types import SimpleNamespace
from urllib.parse import parse_qs, urlparse

import httpx
import pytest

from app.config import Settings
from app.infrastructure.credential_store import EncryptedCredentialStore, StoredCredential
from app.infrastructure.oauth import ChatGptOAuthService
from app.infrastructure.provider_registry import FallbackLLMProvider, build_provider
from app.infrastructure.providers.mock import MockLLMProvider
from app.infrastructure.providers.chatgpt import ChatGptOAuthProvider, ChatGptProviderError
from tests.test_service import evidence, request_with


def encryption_key() -> str:
    return urlsafe_b64encode(b"0123456789abcdef0123456789abcdef").decode()


def settings(tmp_path: Path, **overrides: object) -> Settings:
    values = {
        "enabled": True,
        "provider": "openai_chatgpt",
        "model": "fixture-model",
        "chatgpt_oauth_enabled": True,
        "chatgpt_credential_handle": "chatgpt-fixture",
        "credential_encryption_key": encryption_key(),
        "credential_store_path": str(tmp_path / "credentials.sqlite3"),
        "oauth_store_path": str(tmp_path / "oauth.sqlite3"),
    }
    values.update(overrides)
    return Settings(**values)


def test_registry_defaults_to_mock_and_selects_chatgpt(tmp_path: Path) -> None:
    assert build_provider(Settings()).name == "mock"
    assert isinstance(build_provider(settings(tmp_path)), ChatGptOAuthProvider)


def test_chatgpt_requires_explicit_oauth_configuration() -> None:
    with pytest.raises(ValueError, match="OAUTH_ENABLED"):
        build_provider(Settings(enabled=True, provider="openai_chatgpt"))


def test_fallback_is_only_used_when_explicitly_configured(tmp_path: Path) -> None:
    provider = build_provider(settings(tmp_path, fallback_provider="mock"))
    assert provider.name == "openai_chatgpt"
    assert provider.provenance_classification == "EXTERNAL_MODEL"


def test_fallback_result_is_classified_without_exposing_failure_payload() -> None:
    class FailingProvider:
        name = "external-fixture"
        model = "fixture-model"
        provenance_classification = "EXTERNAL_MODEL"

        def interpret(self, request):
            raise RuntimeError("provider secret should not be persisted")

    provider = FallbackLLMProvider(FailingProvider(), MockLLMProvider())
    result = provider.interpret(request_with([evidence()]))

    assert result.suggested_decision == "REVIEW_CANDIDATE"
    assert provider.provenance_classification == "FALLBACK"
    assert provider.fallback_used is True
    assert provider.fallback_reason == "RuntimeError"
    assert "secret" not in provider.fallback_reason.lower()


def test_credentials_are_encrypted_and_resolved_by_opaque_handle(tmp_path: Path) -> None:
    path = tmp_path / "credentials.sqlite3"
    store = EncryptedCredentialStore(str(path), encryption_key())
    store.put(StoredCredential("handle", "chatgpt", "access-secret", "refresh-secret",
                               datetime.now(timezone.utc) + timedelta(hours=1), "client", "scope"))
    raw = path.read_bytes()
    assert b"access-secret" not in raw
    assert store.get("handle", "chatgpt").access_token == "access-secret"


def test_oauth_start_creates_pkce_transaction(tmp_path: Path) -> None:
    config = settings(tmp_path, chatgpt_ext_agent_host_id="urn:uuid:test-host")
    service = ChatGptOAuthService(
        config, EncryptedCredentialStore(config.credential_store_path, config.credential_encryption_key)
    )
    location = service.start()
    query = parse_qs(urlparse(location).query)
    assert query["code_challenge_method"] == ["S256"]
    assert query["state"]
    assert query["nonce"]
    assert query["code_challenge"]
    assert query["client_id"] == ["dynamic_agent_client"]
    assert query["agent_name_hint"] == ["Trading OS"]
    assert query["ext_agent_host_id"] == ["urn:uuid:test-host"]


def test_chatgpt_provider_parses_completed_structured_stream(tmp_path: Path, monkeypatch) -> None:
    config = settings(tmp_path)
    credentials = EncryptedCredentialStore(config.credential_store_path, config.credential_encryption_key)
    credentials.put(StoredCredential("chatgpt-fixture", "chatgpt", "fixture-token", None, None,
                                     "client", "resource.invoke chatgpt.tokens.use.direct"))

    class Response:
        status_code = 200

        def __enter__(self):
            return self

        def __exit__(self, *_):
            return False

        def iter_lines(self):
            yield "event: response.output_text.delta"
            yield 'data: {"delta":"{\\"marketSummary\\":\\"summary\\",\\"primaryScenario\\":\\"scenario\\",\\"supportingArguments\\":[],\\"contradictions\\":[],\\"missingInformation\\":[],\\"confirmationConditions\\":[],\\"invalidationConditions\\":[],\\"uncertainty\\":[],\\"suggestedDecision\\":\\"WAIT\\"}"}'
            yield ""
            yield "event: response.completed"
            yield 'data: {"type":"response.completed"}'
            yield ""

    class Client:
        def __init__(self, **kwargs):
            self.headers = None

        def __enter__(self):
            return self

        def __exit__(self, *_):
            return False

        def stream(self, method, url, *, headers, json):
            self.headers = headers
            assert json["model"] == "fixture-model"
            assert json["store"] is False
            assert "Evidence references must be copied exactly" in json["input"][0]["content"]
            return Response()

    client = Client()
    monkeypatch.setattr("app.infrastructure.providers.chatgpt.httpx.Client", lambda **kwargs: client)
    provider = ChatGptOAuthProvider(config)
    request = request_with([evidence()])
    result = provider.interpret(request)
    assert result.suggested_decision == "WAIT"
    assert client.headers == {"Authorization": "Bearer fixture-token"}


def test_chatgpt_provider_rejects_incomplete_stream(tmp_path: Path, monkeypatch) -> None:
    config = settings(tmp_path)
    EncryptedCredentialStore(config.credential_store_path, config.credential_encryption_key).put(
        StoredCredential("chatgpt-fixture", "chatgpt", "fixture-token", None, None,
                         "client", "resource.invoke chatgpt.tokens.use.direct")
    )

    class Response:
        status_code = 200

        def __enter__(self): return self
        def __exit__(self, *_): return False
        def iter_lines(self):
            yield 'data: {"type":"response.incomplete","response":{"incomplete_details":{"reason":"max_output_tokens"}}}'
            yield ""

    class Client:
        def __init__(self, **kwargs): pass
        def __enter__(self): return self
        def __exit__(self, *_): return False
        def stream(self, *args, **kwargs): return Response()

    monkeypatch.setattr("app.infrastructure.providers.chatgpt.httpx.Client", Client)
    provider = ChatGptOAuthProvider(config)
    request = request_with([evidence()])
    with pytest.raises(ChatGptProviderError, match="max_output_tokens"):
        provider.interpret(request)
