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
    InterpretationRequest,
    MarketThesisInput,
    ThesisEvidence,
    Trace,
    RequestedAnalysis,
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


def test_unknown_evidence_reference_is_rejected():
    with pytest.raises(InvalidInterpretation):
        InterpretationService(InvalidReferenceProvider()).interpret(request_with([evidence()]))


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
