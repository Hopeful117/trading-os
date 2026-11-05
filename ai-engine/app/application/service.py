from __future__ import annotations

from dataclasses import dataclass

from app.application.ports import LLMProvider
from app.domain.models import InterpretationDraft, InterpretationRequest


class InvalidInterpretation(ValueError):
    pass


class EvidenceLimitExceeded(ValueError):
    pass


@dataclass(frozen=True)
class InterpretationService:
    provider: LLMProvider

    def interpret(self, request: InterpretationRequest, max_evidence: int | None = None) -> InterpretationDraft:
        if max_evidence is not None and len(request.thesis.evidence) > max_evidence:
            raise EvidenceLimitExceeded("evidence count exceeds configured limit")
        evidence_ids = {item.evidence_id for item in request.thesis.evidence}
        draft = self.provider.interpret(request)
        references = [
            reference
            for argument in [*draft.supporting_arguments, *draft.contradictions]
            for reference in argument.evidence_refs
        ]
        unknown = set(references) - evidence_ids
        if unknown:
            raise InvalidInterpretation("provider returned unknown evidence reference")
        return draft
