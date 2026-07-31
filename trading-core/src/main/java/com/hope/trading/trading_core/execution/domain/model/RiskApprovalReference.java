package com.hope.trading.trading_core.execution.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RiskApprovalReference(UUID evaluationId, Decision decision, Instant approvedAt) {
    public RiskApprovalReference {
        Objects.requireNonNull(evaluationId); Objects.requireNonNull(decision);
        Objects.requireNonNull(approvedAt);
    }
    public enum Decision { APPROVED, APPROVED_WITH_WARNINGS }
}
