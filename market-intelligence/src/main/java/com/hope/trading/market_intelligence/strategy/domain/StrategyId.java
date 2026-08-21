package com.hope.trading.market_intelligence.strategy.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable logical identity of a trading Strategy, independent of any version.
 */
public record StrategyId(UUID value) {

    public StrategyId {
        Objects.requireNonNull(value, "StrategyId value is required");
    }

    public static StrategyId random() {
        return new StrategyId(UUID.randomUUID());
    }
}
