from app.domain.models import (
    Argument,
    InterpretationDraft,
    InterpretationPhase,
    InterpretationRequest,
    SuggestedDecision,
)


class MockLLMProvider:
    name = "mock"
    model = "deterministic-fixture"
    provenance_classification = "MOCK"

    def interpret(self, request: InterpretationRequest) -> InterpretationDraft:
        evidence = request.thesis.evidence
        missing = [
            item.explanation
            for item in evidence
            if item.role == "MISSING" or item.availability != "AVAILABLE"
        ]
        decision = (
            SuggestedDecision.INSUFFICIENT_DATA
            if missing or request.thesis.state in {"NO_TRADE", "UNKNOWN"}
            else SuggestedDecision.REVIEW_CANDIDATE
        )
        reconciliations = []
        if request.phase == InterpretationPhase.FINAL_SYNTHESIS and request.synthesis_context:
            for index, hypothesis in enumerate(request.synthesis_context.initial_hypotheses):
                references = (
                    request.synthesis_context.follow_up_evidence.findings[0].evidence_references[:1]
                    if request.synthesis_context.follow_up_evidence.findings else []
                )
                reconciliations.append({
                    "hypothesis_id": f"hypothesis-{index}",
                    "hypothesis": hypothesis,
                    "effect": "INCONCLUSIVE" if not references else "STRENGTHENED",
                    "explanation": "The bounded follow-up was evaluated against the initial hypothesis.",
                    "evidence_references": references,
                })
        return InterpretationDraft(
            market_summary=f"Deterministic thesis state: {request.thesis.state}.",
            primary_scenario=(
                request.thesis.scenario.narrative
                if request.thesis.scenario
                else "No primary scenario is available."
            ),
            supporting_arguments=[
                Argument(text=item.explanation, evidence_refs=[item.evidence_id])
                for item in evidence
                if item.role == "SUPPORTING" and item.availability == "AVAILABLE"
            ],
            contradictions=[
                Argument(text=item.explanation, evidence_refs=[item.evidence_id])
                for item in evidence
                if item.role == "CONTRADICTING"
            ],
            missing_information=missing,
            confirmation_conditions=(
                request.thesis.scenario.confirmation_conditions
                if request.thesis.scenario else []
            ),
            invalidation_conditions=(
                request.thesis.scenario.invalidation_conditions
                if request.thesis.scenario else []
            ),
            uncertainty=list(request.authorized_context.diagnostics),
            suggested_decision=decision,
            requested_analysis=(
                {
                    "capability_id": "market-structure-analysis",
                    "capability_version": "1.1.0",
                    "market_id": request.thesis.market_id,
                    "reason": "Test whether the supplied thesis scenario is supported by current structure evidence.",
                    "expected_evidence": "Market structure assessment for the selected market.",
                }
                if request.research_mode == "ADAPTIVE_RESEARCH" and request.phase == InterpretationPhase.INITIAL
                else None
            ),
            reconciliations=reconciliations,
        )
