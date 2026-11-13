from datetime import datetime, timezone
from uuid import uuid4

import pytest

from app.application.service import InvalidInterpretation, InterpretationService
from app.domain.models import (
    Argument,
    AuthorizedContext,
    ContextSection,
    ExecutionPolicy,
    InterpretationDraft,
    InterpretationPhase,
    InterpretationRequest,
    MarketThesisInput,
    ThesisEvidence,
    Trace,
    RequestedAnalysis,
    FollowUpEvidence,
    SynthesisContext,
    SynthesisFinding,
    HypothesisProposal,
)


def request_with(evidence: list[ThesisEvidence]) -> InterpretationRequest:
    market_id = uuid4()
    now = datetime.now(timezone.utc)
    return InterpretationRequest(
        execution_id=uuid4(),
        capabilities=["ai-market-reasoning"],
        authorized_context=AuthorizedContext(
            market_id=market_id,
            mode="ACTIVE",
            schema_version="analysis-context-v1",
            quality="COMPLETE",
            diagnostics=[],
            fingerprint="a" * 64,
            sections={
                "MARKET_IDENTITY": ContextSection(
                    type="MARKET_IDENTITY", status="AVAILABLE", sensitivity="PUBLIC"
                )
            },
        ),
        thesis=MarketThesisInput(
            thesis_id=uuid4(),
            lineage_id=uuid4(),
            version=1,
            market_id=market_id,
            instrument="BTC/USD",
            state="WATCH",
            evidence=evidence,
            reasons=[],
            evaluated_at=now,
            source_cutoff=now,
            valid_from=now,
            composer_version="market-thesis-v1",
            input_fingerprint="b" * 64,
        ),
        policy=ExecutionPolicy(
            maximum_duration_ms=5_000,
            maximum_ai_requests=1,
            context_limit_bytes=100_000,
            max_output_tokens=500,
        ),
        trace=Trace(request_id="request-1", trace_id="trace-1", contract_version="v1"),
        idempotency_key="idempotency-1",
    )


def evidence(role: str = "SUPPORTING") -> ThesisEvidence:
    now = datetime.now(timezone.utc)
    return ThesisEvidence(
        evidence_id=uuid4(),
        role=role,
        availability="AVAILABLE",
        source="TREND_CONTEXT",
        reference="finding-1",
        explanation="Trend evidence",
        observed_at=now,
        source_cutoff=now,
    )


class InvalidReferenceProvider:
    name = "test"
    model = "fixture"

    def interpret(self, request):
        return InterpretationDraft(
            market_summary="summary",
            primary_scenario="scenario",
            supporting_arguments=[Argument(text="bad", evidence_refs=[uuid4()])],
            contradictions=[],
            missing_information=[],
            confirmation_conditions=[],
            invalidation_conditions=[],
            uncertainty=[],
            suggested_decision="WAIT",
        )


class FinalRequestingProvider:
    def interpret(self, request):
        return InterpretationDraft(
            market_summary="summary",
            primary_scenario="scenario",
            supporting_arguments=[],
            contradictions=[],
            missing_information=[],
            confirmation_conditions=[],
            invalidation_conditions=[],
            uncertainty=[],
            suggested_decision="WAIT",
            requested_analysis=RequestedAnalysis(
                capability_id="market-structure-analysis",
                capability_version="1.1.0",
                market_id=request.thesis.market_id,
                reason="another call",
                expected_evidence="more",
            ),
        )


class FinalSynthesisProvider:
    def __init__(self, evidence_id):
        self.evidence_id = evidence_id

    def interpret(self, request):
        return InterpretationDraft(
            market_summary="revised summary",
            primary_scenario="revised scenario",
            supporting_arguments=[],
            contradictions=[],
            missing_information=[],
            confirmation_conditions=[],
            invalidation_conditions=[],
            uncertainty=[],
            suggested_decision="WAIT",
            reconciliations=[{
                "hypothesis_id": "hypothesis-1",
                "hypothesis": "initial hypothesis",
                "effect": "STRENGTHENED",
                "explanation": "follow-up supports the hypothesis",
                "evidence_references": [self.evidence_id],
            }],
        )


def test_unknown_evidence_reference_is_rejected():
    with pytest.raises(InvalidInterpretation):
        InterpretationService(InvalidReferenceProvider()).interpret(request_with([evidence()]))


class InvalidHypothesisReferenceProvider:
    def interpret(self, request):
        return InterpretationDraft(
            market_summary="summary",
            primary_scenario="scenario",
            supporting_arguments=[],
            contradictions=[],
            missing_information=[],
            confirmation_conditions=[],
            invalidation_conditions=[],
            uncertainty=[],
            suggested_decision="WAIT",
            hypothesis_proposals=[HypothesisProposal(
                hypothesis_id="h-1",
                observed_behavior="observed",
                proposed_relationship="relationship",
                expected_outcome="outcome",
                evaluation_horizon=["4h"],
                applicability_conditions=["condition"],
                falsification_conditions=["falsifier"],
                supporting_evidence_references=[uuid4()],
            )],
        )


def test_unknown_hypothesis_evidence_reference_is_rejected():
    request = request_with([evidence()]).model_copy(update={
        "thesis": None,
        "phase": InterpretationPhase.HYPOTHESIS_DISCOVERY,
        "research_mode": "HYPOTHESIS_DISCOVERY",
    })
    with pytest.raises(InvalidInterpretation):
        InterpretationService(InvalidHypothesisReferenceProvider()).interpret(request)


def test_adaptive_request_is_strict_and_serializes_with_camel_case():
    request = RequestedAnalysis(
        capability_id="market-structure-analysis",
        capability_version="1.1.0",
        market_id=uuid4(),
        reason="Test structure",
        expected_evidence="Levels",
    )

    assert request.model_dump(by_alias=True)["capabilityId"] == "market-structure-analysis"
    with pytest.raises(ValueError):
        RequestedAnalysis(
            capability_id="market-structure-analysis",
            capability_version="1.1.0",
            market_id=uuid4(),
            reason="",
            expected_evidence="Levels",
        )


def test_final_synthesis_accepts_follow_up_evidence_reference():
    request = request_with([evidence()])
    follow_up_id = uuid4()
    final_evidence = uuid4()
    request = request.model_copy(update={
        "phase": InterpretationPhase.FINAL_SYNTHESIS,
        "synthesis_context": SynthesisContext(
            objective="reconcile",
            market_id=request.thesis.market_id,
            initial_hypotheses=["initial hypothesis"],
            initial_findings=[],
            follow_up_evidence=FollowUpEvidence(
                execution_id=follow_up_id,
                capability_id="market-structure-analysis",
                capability_version="1.1.0",
                status="COMPLETE",
                findings=[SynthesisFinding(
                    finding_id=str(final_evidence),
                    title="follow-up",
                    description="new evidence",
                    evidence_references=[final_evidence],
                )],
                diagnostics=[],
            ),
        ),
    })

    result = InterpretationService(FinalSynthesisProvider(final_evidence)).interpret(request)

    assert result.reconciliations[0].effect == "STRENGTHENED"


def test_final_synthesis_cannot_request_another_analysis():
    request = request_with([evidence()])
    request = request.model_copy(update={
        "phase": InterpretationPhase.FINAL_SYNTHESIS,
        "synthesis_context": SynthesisContext(
            objective="reconcile",
            market_id=request.thesis.market_id,
            initial_hypotheses=[],
            initial_findings=[],
            follow_up_evidence=FollowUpEvidence(
                execution_id=uuid4(),
                capability_id="market-structure-analysis",
                capability_version="1.1.0",
                status="COMPLETE",
                findings=[],
                diagnostics=[],
            ),
        ),
    })

    with pytest.raises(InvalidInterpretation, match="cannot request"):
        InterpretationService(FinalRequestingProvider()).interpret(request)
