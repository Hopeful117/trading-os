from app.domain.models import (
    Argument,
    InterpretationDraft,
    InterpretationRequest,
    SuggestedDecision,
)


class MockLLMProvider:
    name = "mock"
    model = "deterministic-fixture"

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
        )
