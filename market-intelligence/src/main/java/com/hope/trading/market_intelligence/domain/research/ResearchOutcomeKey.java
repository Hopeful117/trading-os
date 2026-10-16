package com.hope.trading.market_intelligence.domain.research;

import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ResearchOutcomeKey(UUID marketId, Instant evaluationCutoff, OutcomeHorizon horizon) {
    public ResearchOutcomeKey {
        Objects.requireNonNull(marketId, "market ID is required");
        Objects.requireNonNull(evaluationCutoff, "evaluation cutoff is required");
        Objects.requireNonNull(horizon, "horizon is required");
    }
}
