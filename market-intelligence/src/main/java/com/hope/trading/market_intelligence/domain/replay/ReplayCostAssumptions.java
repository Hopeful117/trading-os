package com.hope.trading.market_intelligence.domain.replay;

import java.math.BigDecimal;
import java.util.Objects;

public record ReplayCostAssumptions(
        String version,
        BigDecimal spread,
        BigDecimal transactionCost
) {
    public ReplayCostAssumptions {
        Objects.requireNonNull(version, "cost assumption version is required");
        if (version.isBlank()) throw new IllegalArgumentException("cost assumption version must not be blank");
        spread = nonNegative(spread, "spread");
        transactionCost = nonNegative(transactionCost, "transaction cost");
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.signum() < 0) throw new IllegalArgumentException(field + " must not be negative");
        return value.stripTrailingZeros();
    }
}
