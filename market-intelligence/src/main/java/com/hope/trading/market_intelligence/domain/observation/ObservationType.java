package com.hope.trading.market_intelligence.domain.observation;

import java.util.Objects;

public record ObservationType(String value) {
    public ObservationType {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Observation type is required");
    }
}
