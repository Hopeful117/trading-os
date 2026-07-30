package com.hope.trading.risk.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.hope.trading.risk.domain.RiskTypes.ValidationMode;

public record RiskEvaluationRequest(
        UUID evaluationId, UUID correlationId, ValidationMode mode,
        ProposedTrade proposedTrade, Instant requestedAt
) {
    public RiskEvaluationRequest {
        Objects.requireNonNull(evaluationId); Objects.requireNonNull(correlationId);
        Objects.requireNonNull(mode); Objects.requireNonNull(requestedAt);
        if (mode == ValidationMode.PRE_TRADE && proposedTrade == null) {
            throw new IllegalArgumentException("PRE_TRADE requires a proposed trade");
        }
    }
}
