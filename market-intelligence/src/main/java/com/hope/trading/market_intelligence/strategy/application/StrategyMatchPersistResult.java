package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;

import java.util.Objects;

/**
 * Explicit outcome of one idempotent persistence attempt.
 */
public record StrategyMatchPersistResult(StrategyMatch match, boolean created) {

    public StrategyMatchPersistResult {
        Objects.requireNonNull(match, "match is required");
    }

    public static StrategyMatchPersistResult created(StrategyMatch match) {
        return new StrategyMatchPersistResult(match, true);
    }

    public static StrategyMatchPersistResult alreadyExists(StrategyMatch match) {
        return new StrategyMatchPersistResult(match, false);
    }
}
