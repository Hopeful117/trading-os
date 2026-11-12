from __future__ import annotations

import json

from openai import OpenAI

from app.config import Settings
from app.domain.models import InterpretationDraft, InterpretationRequest


class OpenAILLMProvider:
    name = "openai"
    provenance_classification = "EXTERNAL_MODEL"

    def __init__(self, settings: Settings) -> None:
        if not settings.api_key:
            raise ValueError("AI_ENGINE_API_KEY is required when the OpenAI provider is enabled")
        self.model = settings.model
        self._client = OpenAI(
            api_key=settings.api_key,
            timeout=settings.timeout_seconds,
            max_retries=settings.max_retries,
        )

    def interpret(self, request: InterpretationRequest) -> InterpretationDraft:
        payload = request.model_dump(mode="json", exclude={"policy", "trace"})
        evidence_ids = {str(item.evidence_id) for item in request.thesis.evidence}
        if request.synthesis_context is not None:
            for finding in request.synthesis_context.initial_findings:
                evidence_ids.update(str(value) for value in finding.evidence_references)
            for finding in request.synthesis_context.follow_up_evidence.findings:
                evidence_ids.update(str(value) for value in finding.evidence_references)
        response = self._client.chat.completions.create(
            model=self.model,
            messages=[
                {
                    "role": "system",
                    "content": (
                        "Interpret the supplied deterministic Market Thesis. "
                        "Return only JSON matching the requested schema. "
                        "Never invent prices, orders, risk decisions or evidence references. "
                        f"Use only these authorized evidence references: {sorted(evidence_ids)}"
                    ),
                },
                {"role": "user", "content": json.dumps(payload)},
            ],
            response_format={"type": "json_object"},
            max_tokens=request.policy.max_output_tokens,
        )
        content = response.choices[0].message.content
        if not content:
            raise ValueError("OpenAI returned an empty response")
        return InterpretationDraft.model_validate_json(content)
