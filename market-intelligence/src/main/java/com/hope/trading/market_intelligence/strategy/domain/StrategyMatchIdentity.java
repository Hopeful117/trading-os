package com.hope.trading.market_intelligence.strategy.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Logical live identity of one StrategyMatch. Two matches sharing this tuple
 * are the same logical fact; the database unique constraint on these columns
 * is the authoritative idempotency protection.
 *
 * <p>Story 0013 (Backtest) may extend this concept additively once a generalized
 * evaluation-source provenance model exists.</p>
 */
public record StrategyMatchIdentity(
        UUID strategyId,
        int strategyVersion,
        UUID marketId,
        UUID analysisExecutionId,
        String contextDigest
) {
    public StrategyMatchIdentity {
        Objects.requireNonNull(strategyId, "strategyId is required");
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(analysisExecutionId, "analysisExecutionId is required");
        Objects.requireNonNull(contextDigest, "contextDigest is required");
        if (strategyVersion < 1) {
            throw new IllegalArgumentException("strategy version starts at 1");
        }
    }
}
