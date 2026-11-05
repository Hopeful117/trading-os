from __future__ import annotations

from typing import Protocol

from app.domain.models import InterpretationDraft, InterpretationRequest


class LLMProvider(Protocol):
    name: str
    model: str

    def interpret(self, request: InterpretationRequest) -> InterpretationDraft:
        ...
