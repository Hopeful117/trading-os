package com.hope.trading.risk.snapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record MarketSnapshot(long version, Instant capturedAt, Map<String, BigDecimal> prices) {
    public MarketSnapshot {
        if (version < 1) throw new IllegalArgumentException("version starts at 1");
        Objects.requireNonNull(capturedAt);
        prices = Map.copyOf(prices);
        if (prices.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getValue() == null
                || e.getValue().signum() <= 0)) throw new IllegalArgumentException("Invalid market price");
    }
}
