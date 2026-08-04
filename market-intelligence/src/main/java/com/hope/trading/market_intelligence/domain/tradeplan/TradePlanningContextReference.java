package com.hope.trading.market_intelligence.domain.tradeplan;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TradePlanningContextReference(UUID id, long version, Instant capturedAt) {
    public TradePlanningContextReference {
        Objects.requireNonNull(id);
        Objects.requireNonNull(capturedAt);
        if (version < 1) throw new IllegalArgumentException("Context version starts at 1");
    }
}
