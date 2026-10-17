package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record SimulatedExecution(
        StrategyIntent.Side side,
        BigDecimal quantity,
        BigDecimal fillPrice,
        Instant executedAt,
        String executionModelVersion,
        String sourceFingerprint
) {
    public SimulatedExecution {
        Objects.requireNonNull(side, "side is required");
        if (Objects.requireNonNull(quantity, "quantity is required").signum() <= 0
                || Objects.requireNonNull(fillPrice, "fillPrice is required").signum() <= 0) {
            throw new IllegalArgumentException("quantity and fillPrice must be positive");
        }
        Objects.requireNonNull(executedAt, "executedAt is required");
        executionModelVersion = required(executionModelVersion, "executionModelVersion");
        sourceFingerprint = required(sourceFingerprint, "sourceFingerprint");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
