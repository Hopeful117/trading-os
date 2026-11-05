import time
import base64
from datetime import datetime, timedelta, timezone

import jwt

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from tests.test_service import evidence, request_with


def test_health_is_disabled_by_default():
    client = TestClient(create_app(Settings(enabled=False, require_service_auth=False)))

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "enabled": False,
        "shadowMode": True,
        "provider": "mock",
    }


def test_disabled_engine_rejects_analysis():
    client = TestClient(create_app(Settings(enabled=False, require_service_auth=False)))

    response = client.post(
        "/internal/v1/analyses",
        json=request_with([evidence()]).model_dump(mode="json"),
    )

    assert response.status_code == 503


def test_non_shadow_mode_is_rejected_until_canonical_write_flow_exists():
    client = TestClient(create_app(Settings(
        enabled=True, shadow_mode=False, require_service_auth=False
    )))

    response = client.post(
        "/internal/v1/analyses",
        json=request_with([evidence()]).model_dump(mode="json"),
    )

    assert response.status_code == 503


def test_internal_analysis_requires_service_authorization():
    client = TestClient(create_app(Settings(enabled=True, provider="mock")))

    response = client.post(
        "/internal/v1/analyses",
        json=request_with([evidence()]).model_dump(mode="json"),
    )

    assert response.status_code == 401


def test_internal_analysis_accepts_service_authorization_header():
    secret = base64.b64encode(b"a" * 32).decode()
    client = TestClient(create_app(Settings(
        enabled=True,
        provider="mock",
        service_jwt_secret=secret,
    )))
    now = datetime.now(timezone.utc)
    token = jwt.encode(
        {
            "iss": "market-intelligence",
            "sub": "market-intelligence",
            "aud": "ai-engine",
            "iat": now,
            "exp": now + timedelta(minutes=1),
        },
        b"a" * 32,
        algorithm="HS256",
    )

    response = client.post(
        "/internal/v1/analyses",
        json=request_with([evidence()]).model_dump(mode="json"),
        headers={"X-Service-Authorization": f"Bearer {token}"},
    )

    assert response.status_code == 202


def test_mock_analysis_is_async_and_returns_validated_result():
    client = TestClient(create_app(Settings(
        enabled=True, provider="mock", require_service_auth=False
    )))
    request = request_with([evidence()])

    accepted = client.post(
        "/internal/v1/analyses",
        json=request.model_dump(mode="json"),
    )
    assert accepted.status_code == 202

    execution_id = accepted.json()["executionId"]
    for _ in range(20):
        result = client.get(f"/internal/v1/analyses/{execution_id}/result")
        if result.status_code == 200:
            break
        time.sleep(0.01)

    assert result.status_code == 200
    assert result.json()["status"] == "COMPLETED"
    assert result.json()["result"]["suggestedDecision"] == "REVIEW_CANDIDATE"


def test_execution_id_reuse_with_different_request_is_rejected():
    client = TestClient(create_app(Settings(
        enabled=True, provider="mock", require_service_auth=False
    )))
    request = request_with([evidence()])

    first = client.post(
        "/internal/v1/analyses",
        json=request.model_dump(mode="json"),
    )
    conflict = client.post(
        "/internal/v1/analyses",
        json=request.model_copy(update={"idempotency_key": "different"}).model_dump(mode="json"),
    )

    assert first.status_code == 202
    assert conflict.status_code == 409
