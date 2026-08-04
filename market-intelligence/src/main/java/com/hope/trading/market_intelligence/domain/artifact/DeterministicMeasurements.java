package com.hope.trading.market_intelligence.domain.artifact;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record DeterministicMeasurements(
        String title,
        String explanation,
        Map<String, BigDecimal> values,
        Instant observedAt
) implements ArtifactContent {
    public DeterministicMeasurements {
        values = Map.copyOf(values);
    }
}
