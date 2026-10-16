package com.hope.trading.market_intelligence.application.outcome;

import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MarketOutcomeEvaluationRequest(
        UUID marketId,
        Instant evaluationCutoff,
        OutcomeHorizon horizon,
        UUID analysisExecutionId
) {
    public MarketOutcomeEvaluationRequest {
        Objects.requireNonNull(marketId, "market ID is required");
        Objects.requireNonNull(evaluationCutoff, "evaluation cutoff is required");
        Objects.requireNonNull(horizon, "horizon is required");
    }
}
