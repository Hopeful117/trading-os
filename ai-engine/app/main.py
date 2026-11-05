from __future__ import annotations

import base64
from fastapi import Depends, FastAPI, Header, HTTPException, Response, status
import jwt

from app.application.service import InterpretationService
from app.config import Settings, settings
from app.domain.models import InterpretationRequest
from app.infrastructure.execution_store import ExecutionStore
from app.infrastructure.providers.mock import MockLLMProvider
from app.infrastructure.providers.openai import OpenAILLMProvider


def create_app(config: Settings | None = None) -> FastAPI:
    config = config or settings
    provider = MockLLMProvider()
    if config.enabled and config.provider.lower() == "openai":
        provider = OpenAILLMProvider(config)
    store = ExecutionStore(InterpretationService(provider), config.max_evidence)
    app = FastAPI(title="Trading OS AI Engine", version="0.1.0")
    app.state.settings = config
    app.state.store = store

    def authorize_service(
        authorization: str | None = Header(default=None, alias="X-Service-Authorization")
    ) -> None:
        if not config.require_service_auth:
            return
        if not config.service_jwt_secret or not authorization or not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Service authorization is required")
        try:
            jwt.decode(
                authorization.removeprefix("Bearer ").strip(),
                base64.b64decode(config.service_jwt_secret),
                algorithms=["HS256"],
                issuer=config.service_jwt_issuer,
                audience=config.service_jwt_audience,
            )
        except (ValueError, jwt.PyJWTError) as error:
            raise HTTPException(status_code=401, detail="Invalid service authorization") from error

    @app.get("/health")
    def health() -> dict[str, object]:
        return {
            "status": "UP",
            "enabled": config.enabled,
            "shadowMode": config.shadow_mode,
            "provider": provider.name,
        }

    @app.post("/internal/v1/analyses", status_code=status.HTTP_202_ACCEPTED)
    def submit(
        request: InterpretationRequest,
        _: None = Depends(authorize_service),
    ) -> dict[str, object]:
        if not config.enabled:
            raise HTTPException(status_code=503, detail="AI Engine is disabled")
        if not config.shadow_mode:
            raise HTTPException(status_code=503, detail="Only shadow mode is supported")
        if len(request.model_dump_json()) > config.max_context_bytes:
            raise HTTPException(status_code=413, detail="Analysis context exceeds configured limit")
        try:
            record = store.submit(request)
        except ValueError as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        return {
            "executionId": str(record.request.execution_id),
            "status": record.status,
            "acceptedAt": record.accepted_at,
        }

    @app.get("/internal/v1/analyses/{execution_id}")
    def progress(
        execution_id: str,
        _: None = Depends(authorize_service),
    ):
        try:
            from uuid import UUID
            value = store.progress(UUID(execution_id))
        except ValueError as error:
            raise HTTPException(status_code=400, detail="Invalid execution ID") from error
        if value is None:
            raise HTTPException(status_code=404, detail="Analysis not found")
        return value

    @app.get("/internal/v1/analyses/{execution_id}/result")
    def result(
        execution_id: str,
        response: Response,
        _: None = Depends(authorize_service),
    ):
        from uuid import UUID
        try:
            parsed_id = UUID(execution_id)
            value = store.result(parsed_id)
            progress_value = store.progress(parsed_id)
        except ValueError as error:
            raise HTTPException(status_code=400, detail="Invalid execution ID") from error
        if progress_value is None:
            raise HTTPException(status_code=404, detail="Analysis not found")
        if value is None:
            response.status_code = status.HTTP_202_ACCEPTED
            return progress_value
        return value

    @app.post("/internal/v1/analyses/{execution_id}/cancel")
    def cancel(
        execution_id: str,
        _: None = Depends(authorize_service),
    ):
        from uuid import UUID
        try:
            cancelled = store.cancel(UUID(execution_id))
        except ValueError as error:
            raise HTTPException(status_code=400, detail="Invalid execution ID") from error
        if not cancelled:
            raise HTTPException(status_code=409, detail="Analysis cannot be cancelled")
        return {"status": "CANCELLED"}

    return app


app = create_app()
