package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record StrategyIntent(
        String instrument,
        Side side,
        OrderType orderType,
        BigDecimal quantity,
        Instant decisionAt
) {
    public StrategyIntent {
        instrument = required(instrument, "instrument");
        Objects.requireNonNull(side, "side is required");
        Objects.requireNonNull(orderType, "orderType is required");
        if (Objects.requireNonNull(quantity, "quantity is required").signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Objects.requireNonNull(decisionAt, "decisionAt is required");
    }

    public enum Side { BUY, SELL }
    public enum OrderType { MARKET, LIMIT }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
