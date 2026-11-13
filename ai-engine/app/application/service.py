from __future__ import annotations

from dataclasses import dataclass

from app.application.ports import LLMProvider
from app.domain.models import InterpretationDraft, InterpretationPhase, InterpretationRequest


class InvalidInterpretation(ValueError):
    pass


class EvidenceLimitExceeded(ValueError):
    pass


@dataclass(frozen=True)
class InterpretationService:
    provider: LLMProvider

    def interpret(self, request: InterpretationRequest, max_evidence: int | None = None) -> InterpretationDraft:
        thesis_evidence = request.thesis.evidence if request.thesis is not None else []
        if max_evidence is not None and len(thesis_evidence) > max_evidence:
            raise EvidenceLimitExceeded("evidence count exceeds configured limit")
        evidence_ids = {item.evidence_id for item in thesis_evidence}
        if request.synthesis_context is not None:
            evidence_ids.update(
                reference
                for finding in request.synthesis_context.initial_findings
                for reference in finding.evidence_references
            )
            evidence_ids.update(
                reference
                for finding in request.synthesis_context.follow_up_evidence.findings
                for reference in finding.evidence_references
            )
        draft = self.provider.interpret(request)
        references = [
            reference
            for argument in [*draft.supporting_arguments, *draft.contradictions]
            for reference in argument.evidence_refs
        ]
        unknown = set(references) - evidence_ids
        if unknown:
            raise InvalidInterpretation("provider returned unknown evidence reference")
        reconciliation_references = {
            reference
            for reconciliation in draft.reconciliations
            for reference in reconciliation.evidence_references
        }
        unknown_reconciliation = reconciliation_references - evidence_ids
        if unknown_reconciliation:
            raise InvalidInterpretation("provider returned unknown reconciliation evidence reference")
        context_evidence_ids = set()
        for section in request.authorized_context.sections.values():
            if section.provenance is None:
                continue
            for reference in str(section.provenance.get("sourceReference", "")).split(","):
                try:
                    context_evidence_ids.add(UUID(reference))
                except ValueError:
                    continue
        proposal_references = {
            reference
            for proposal in draft.hypothesis_proposals
            for reference in proposal.supporting_evidence_references
        }
        unknown_proposals = proposal_references - (evidence_ids | context_evidence_ids)
        if unknown_proposals:
            raise InvalidInterpretation("provider returned unknown hypothesis evidence reference")
        if request.phase == InterpretationPhase.HYPOTHESIS_DISCOVERY:
            if draft.reconciliations:
                raise InvalidInterpretation("hypothesis discovery cannot return reconciliations")
        if request.phase == InterpretationPhase.FINAL_SYNTHESIS and draft.requested_analysis is not None:
            raise InvalidInterpretation("final synthesis cannot request another analysis")
        return draft
