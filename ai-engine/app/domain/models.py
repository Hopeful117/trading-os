from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from typing import Any
from uuid import UUID

from pydantic import AliasGenerator, BaseModel, ConfigDict, Field, field_validator, model_validator


def to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part[:1].upper() + part[1:] for part in tail)


class StrictModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=AliasGenerator(validation_alias=to_camel, serialization_alias=to_camel),
        populate_by_name=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


class ExecutionStatus(StrEnum):
    ACCEPTED = "ACCEPTED"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    PARTIAL = "PARTIAL"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class ResultQuality(StrEnum):
    COMPLETE = "COMPLETE"
    PARTIAL = "PARTIAL"
    DEGRADED = "DEGRADED"


class SuggestedDecision(StrEnum):
    WAIT = "WAIT"
    MONITOR = "MONITOR"
    REVIEW_CANDIDATE = "REVIEW_CANDIDATE"
    INSUFFICIENT_DATA = "INSUFFICIENT_DATA"


class ThesisEvidence(StrictModel):
    evidence_id: UUID
    role: str
    availability: str
    severity: str | None = None
    source: str
    reference: str
    timeframe: str | None = None
    explanation: str
    observed_at: datetime
    valid_until: datetime | None = None
    provider: str | None = None
    instrument: str | None = None
    source_fingerprint: str | None = None
    rule_version: str | None = None
    source_cutoff: datetime | None = None
    required: bool = False

    @model_validator(mode="after")
    def validate_dates(self) -> "ThesisEvidence":
        if self.valid_until is not None and self.valid_until < self.observed_at:
            raise ValueError("evidence valid_until cannot precede observed_at")
        return self


class ThesisScenario(StrictModel):
    direction: str
    narrative: str
    confirmation_conditions: list[str] = Field(default_factory=list, max_length=64)
    invalidation_conditions: list[str] = Field(default_factory=list, max_length=64)


class MarketThesisInput(StrictModel):
    thesis_id: UUID
    lineage_id: UUID
    version: int = Field(ge=1)
    market_id: UUID
    instrument: str = Field(min_length=1, max_length=120)
    state: str
    scenario: ThesisScenario | None = None
    evidence: list[ThesisEvidence] = Field(min_length=1, max_length=2_000)
    reasons: list[str] = Field(max_length=256)
    evaluated_at: datetime
    source_cutoff: datetime
    valid_from: datetime
    valid_until: datetime | None = None
    composer_version: str = Field(min_length=1, max_length=100)
    input_fingerprint: str = Field(min_length=1, max_length=128)

    @model_validator(mode="after")
    def validate_temporal_consistency(self) -> "MarketThesisInput":
        if self.evaluated_at < self.source_cutoff:
            raise ValueError("evaluated_at cannot precede source_cutoff")
        if self.valid_from > self.evaluated_at:
            raise ValueError("valid_from cannot follow evaluated_at")
        if self.valid_until is not None and self.valid_until < self.valid_from:
            raise ValueError("valid_until cannot precede valid_from")
        if any(
            evidence.observed_at > self.source_cutoff
            or (evidence.source_cutoff is not None and evidence.source_cutoff > self.source_cutoff)
            for evidence in self.evidence
        ):
            raise ValueError("evidence must be within the thesis source cutoff")
        return self


class ContextSection(StrictModel):
    type: str
    status: str
    sensitivity: str
    payload: dict[str, Any] | list[Any] | str | int | float | bool | None = None
    provenance: dict[str, Any] | None = None
    message: str | None = None


class AuthorizedContext(StrictModel):
    market_id: UUID | None = None
    mode: str | None = None
    objective: str | None = None
    evaluation_cutoff: datetime | None = None
    schema_version: str = Field(min_length=1, max_length=64)
    quality: str
    diagnostics: list[str] = Field(max_length=256)
    fingerprint: str = Field(min_length=1, max_length=128)
    sections: dict[str, ContextSection]


class ExecutionPolicy(StrictModel):
    maximum_duration_ms: int = Field(gt=0, le=300_000)
    maximum_ai_requests: int = Field(ge=0, le=100)
    context_limit_bytes: int = Field(gt=0, le=2_000_000)
    max_output_tokens: int = Field(gt=0, le=8_000)


class Trace(StrictModel):
    request_id: str = Field(min_length=1, max_length=128)
    trace_id: str = Field(min_length=1, max_length=128)
    contract_version: str = Field(min_length=1, max_length=64)


class InterpretationRequest(StrictModel):
    execution_id: UUID
    capabilities: list[str] = Field(min_length=1, max_length=32)
    authorized_context: AuthorizedContext
    thesis: MarketThesisInput
    policy: ExecutionPolicy
    trace: Trace
    idempotency_key: str = Field(min_length=1, max_length=128)

    @field_validator("capabilities")
    @classmethod
    def capabilities_must_be_unique(cls, value: list[str]) -> list[str]:
        if len(value) != len(set(value)):
            raise ValueError("capabilities must be unique")
        return value

    @model_validator(mode="after")
    def validate_context(self) -> "InterpretationRequest":
        if self.authorized_context.market_id not in (None, self.thesis.market_id):
            raise ValueError("context market_id must match thesis market_id")
        evidence_count = len(self.thesis.evidence)
        if evidence_count > 2_000:
            raise ValueError("too many evidence items")
        return self


class Argument(StrictModel):
    text: str = Field(min_length=1, max_length=2_000)
    evidence_refs: list[UUID] = Field(max_length=32)


class InterpretationDraft(StrictModel):
    market_summary: str = Field(min_length=1, max_length=4_000)
    primary_scenario: str = Field(min_length=1, max_length=4_000)
    supporting_arguments: list[Argument] = Field(max_length=64)
    contradictions: list[Argument] = Field(max_length=64)
    missing_information: list[str] = Field(max_length=64)
    confirmation_conditions: list[str] = Field(max_length=64)
    invalidation_conditions: list[str] = Field(max_length=64)
    uncertainty: list[str] = Field(max_length=64)
    suggested_decision: SuggestedDecision


class InterpretationResult(StrictModel):
    execution_id: UUID
    status: ExecutionStatus
    quality: ResultQuality | None = None
    result: InterpretationDraft | None = None
    error_type: str | None = None
    error_message: str | None = None
    provider: str | None = None
    model: str | None = None
    accepted_at: datetime
    completed_at: datetime | None = None

    @model_validator(mode="after")
    def result_matches_status(self) -> "InterpretationResult":
        if self.status in {ExecutionStatus.COMPLETED, ExecutionStatus.PARTIAL} and self.result is None:
            raise ValueError("completed analyses require a result")
        if self.status == ExecutionStatus.FAILED and not self.error_type:
            raise ValueError("failed analyses require an error type")
        return self


class ProgressResponse(StrictModel):
    execution_id: UUID
    status: ExecutionStatus
    updated_at: datetime
    error_type: str | None = None
