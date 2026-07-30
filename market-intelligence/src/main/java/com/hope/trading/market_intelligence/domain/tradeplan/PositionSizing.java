package com.hope.trading.market_intelligence.domain.tradeplan;

import java.math.BigDecimal;
import java.util.Objects;

public record PositionSizing(
        BigDecimal quantity, BigDecimal notional, BigDecimal expectedMonetaryRisk,
        String currency
) {
    public PositionSizing {
        positive(quantity, "quantity");
        positive(notional, "notional");
        positive(expectedMonetaryRisk, "expectedMonetaryRisk");
        currency = Objects.requireNonNull(currency).trim().toUpperCase();
        if (currency.isEmpty()) throw new IllegalArgumentException("currency is required");
    }
    private static void positive(BigDecimal value, String field) {
        if (Objects.requireNonNull(value, field).signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
