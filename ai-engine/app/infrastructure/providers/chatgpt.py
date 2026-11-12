from __future__ import annotations

import json
import re
from datetime import datetime, timezone

import httpx
from openai.lib._pydantic import to_strict_json_schema

from app.config import Settings
from app.domain.models import InterpretationDraft, InterpretationRequest
from app.infrastructure.credential_store import EncryptedCredentialStore
from app.infrastructure.oauth import ChatGptOAuthService, OAuthError


class ChatGptProviderError(RuntimeError):
    pass


class ChatGptOAuthProvider:
    name = "openai_chatgpt"
    provenance_classification = "EXTERNAL_MODEL"

    def __init__(self, config: Settings) -> None:
        if not config.chatgpt_oauth_enabled:
            raise ValueError("AI_ENGINE_CHATGPT_OAUTH_ENABLED is required when ChatGPT is enabled")
        if not config.chatgpt_credential_handle:
            raise ValueError("AI_ENGINE_CHATGPT_CREDENTIAL_HANDLE is required when ChatGPT is enabled")
        self.model = config.model
        self._config = config
        self._credentials = EncryptedCredentialStore(config.credential_store_path, config.credential_encryption_key)
        self._oauth = ChatGptOAuthService(config, self._credentials)

    def interpret(self, request: InterpretationRequest) -> InterpretationDraft:
        started = datetime.now(timezone.utc)
        try:
            credential = self._credentials.get(self._config.chatgpt_credential_handle, "chatgpt")
            if credential.expires_at and credential.expires_at <= started:
                credential = self._oauth.refresh(credential)
            if not {"resource.invoke", "chatgpt.tokens.use.direct"}.issubset(set(credential.scopes.split())):
                raise ChatGptProviderError("ChatGPT plan authorization is incomplete")
            evidence_ids = self._authorized_evidence_ids(request)
            body = {
                "model": self.model,
                "store": False,
                "stream": True,
                "max_output_tokens": min(request.policy.max_output_tokens, self._config.max_output_tokens),
                "input": [
                    {"role": "system", "content": (
                        "Return only JSON matching the requested schema. Never invent evidence or market facts. "
                        "Evidence references must be copied exactly from this authorized list: "
                        + json.dumps(evidence_ids)
                    )},
                    {"role": "user", "content": json.dumps(request.model_dump(mode="json", exclude={"policy", "trace"}))},
                ],
                "text": {"format": {"type": "json_schema", "name": "trading_research_output",
                                     "strict": True, "schema": to_strict_json_schema(InterpretationDraft)}},
            }
            content = self._stream(credential.access_token, body)
            return InterpretationDraft.model_validate_json(content)
        except OAuthError as error:
            raise ChatGptProviderError(str(error)) from error
        except (httpx.HTTPError, ValueError, KeyError, json.JSONDecodeError) as error:
            raise ChatGptProviderError("ChatGPT provider request failed") from error

    @staticmethod
    def _authorized_evidence_ids(request: InterpretationRequest) -> list[str]:
        evidence_ids = {str(item.evidence_id) for item in request.thesis.evidence}
        if request.synthesis_context is not None:
            for finding in request.synthesis_context.initial_findings:
                evidence_ids.update(str(value) for value in finding.evidence_references)
            for finding in request.synthesis_context.follow_up_evidence.findings:
                evidence_ids.update(str(value) for value in finding.evidence_references)
        return sorted(evidence_ids)

    def _stream(self, access_token: str, body: dict) -> str:
        chunks: list[str] = []
        completed = False
        with httpx.Client(timeout=self._config.timeout_seconds) as client:
            with client.stream("POST", self._config.chatgpt_responses_endpoint,
                               headers={"Authorization": f"Bearer {access_token}"}, json=body) as response:
                if response.status_code >= 400:
                    response.read()
                    raise ChatGptProviderError(self._error_message(response))
                event_type = None
                data_lines: list[str] = []
                for line in response.iter_lines():
                    if line.startswith("event:"):
                        event_type = line[6:].strip()
                    elif line.startswith("data:"):
                        data_lines.append(line[5:].strip())
                    elif not line and data_lines:
                        data = json.loads("\n".join(data_lines))
                        received = event_type or data.get("type")
                        if received == "response.output_text.delta":
                            chunks.append(data.get("delta", ""))
                        elif received in {"response.failed", "response.incomplete", "error"}:
                            raise ChatGptProviderError(self._incomplete_message(received, data))
                        elif received == "response.completed":
                            completed = True
                        event_type, data_lines = None, []
        if not completed or not chunks:
            raise ChatGptProviderError("ChatGPT response did not complete")
        return "".join(chunks)

    @staticmethod
    def _error_message(response: httpx.Response) -> str:
        labels: list[str] = []
        try:
            error = response.json().get("error", {})
            for key in ("code", "type"):
                value = error.get(key) if isinstance(error, dict) else None
                if isinstance(value, str) and value:
                    labels.append(f"{key}={re.sub(r'[^A-Za-z0-9_.-]', '_', value)[:80]}")
        except (ValueError, TypeError):
            pass
        return f"ChatGPT provider request failed (HTTP {response.status_code}{'; ' + ', '.join(labels) if labels else ''})"

    @staticmethod
    def _incomplete_message(event_type: str, data: dict) -> str:
        response = data.get("response") if isinstance(data.get("response"), dict) else data
        details = response.get("incomplete_details") if isinstance(response, dict) else None
        reason = details.get("reason") if isinstance(details, dict) else None
        safe = re.sub(r"[^A-Za-z0-9_.-]", "_", reason)[:80] if isinstance(reason, str) else None
        return f"ChatGPT response did not complete ({event_type}{'; reason=' + safe if safe else ''})"
