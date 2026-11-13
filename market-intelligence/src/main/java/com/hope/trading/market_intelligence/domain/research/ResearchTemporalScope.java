package com.hope.trading.market_intelligence.domain.research;

import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record ResearchTemporalScope(
        String timeframe,
        OutcomeHorizon horizon,
        Instant cutoffStart,
        Instant cutoffEnd
) {
    public ResearchTemporalScope {
        Objects.requireNonNull(timeframe, "timeframe is required");
        if (!Set.of("ONE_MINUTE", "FIVE_MINUTES", "FIFTEEN_MINUTES", "THIRTY_MINUTES",
                "ONE_HOUR", "FOUR_HOURS", "ONE_DAY").contains(timeframe)) {
            throw new IllegalArgumentException("unsupported timeframe: " + timeframe);
        }
        Objects.requireNonNull(horizon, "horizon is required");
        Objects.requireNonNull(cutoffStart, "cutoffStart is required");
        Objects.requireNonNull(cutoffEnd, "cutoffEnd is required");
        if (!cutoffStart.isBefore(cutoffEnd)) {
            throw new IllegalArgumentException("cutoffStart must precede cutoffEnd");
        }
    }
}
