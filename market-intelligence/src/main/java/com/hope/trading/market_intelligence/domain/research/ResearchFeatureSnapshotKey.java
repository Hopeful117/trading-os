package com.hope.trading.market_intelligence.domain.research;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ResearchFeatureSnapshotKey(UUID marketId, Instant evaluationCutoff) {
    public ResearchFeatureSnapshotKey {
        Objects.requireNonNull(marketId, "market ID is required");
        Objects.requireNonNull(evaluationCutoff, "evaluation cutoff is required");
    }
}
