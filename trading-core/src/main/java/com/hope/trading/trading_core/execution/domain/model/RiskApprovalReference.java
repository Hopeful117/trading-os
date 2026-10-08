package com.hope.trading.trading_core.execution.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RiskApprovalReference(UUID evaluationId, Decision decision, Instant approvedAt,
                                    long evaluatedTradePlanVersion) {
    public RiskApprovalReference(UUID evaluationId, Decision decision, Instant approvedAt) {
        this(evaluationId, decision, approvedAt, 0);
    }
    public RiskApprovalReference {
        Objects.requireNonNull(evaluationId); Objects.requireNonNull(decision);
        Objects.requireNonNull(approvedAt);
        if (evaluatedTradePlanVersion < 0) {
            throw new IllegalArgumentException("evaluated trade plan version cannot be negative");
        }
    }
    public enum Decision { APPROVED, APPROVED_WITH_WARNINGS }
}
