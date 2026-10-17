package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record SimulatedExecution(
        StrategyIntent.Side side,
        BigDecimal requestedQuantity,
        BigDecimal filledQuantity,
        BigDecimal averageExecutionPrice,
        Instant executedAt,
        SimulatedExecutionStatus status,
        String executionModelVersion,
        String sourceFingerprint
) {
    public SimulatedExecution(
            StrategyIntent.Side side,
            BigDecimal quantity,
            BigDecimal fillPrice,
            Instant executedAt,
            String executionModelVersion,
            String sourceFingerprint
    ) {
        this(side, quantity, quantity, fillPrice, executedAt, SimulatedExecutionStatus.FILLED,
                executionModelVersion, sourceFingerprint);
    }

    public SimulatedExecution {
        Objects.requireNonNull(side, "side is required");
        if (Objects.requireNonNull(requestedQuantity, "requestedQuantity is required").signum() <= 0) {
            throw new IllegalArgumentException("requestedQuantity must be positive");
        }
        if (Objects.requireNonNull(filledQuantity, "filledQuantity is required").signum() < 0
                || filledQuantity.compareTo(requestedQuantity) > 0) {
            throw new IllegalArgumentException("filledQuantity must be between zero and requestedQuantity");
        }
        if (filledQuantity.signum() > 0
                && (Objects.requireNonNull(averageExecutionPrice, "averageExecutionPrice is required").signum() <= 0)) {
            throw new IllegalArgumentException("averageExecutionPrice must be positive for a fill");
        }
        Objects.requireNonNull(status, "status is required");
        if (status == SimulatedExecutionStatus.FILLED
                && filledQuantity.compareTo(requestedQuantity) != 0) {
            throw new IllegalArgumentException("FILLED execution must fill the requested quantity");
        }
        if (status == SimulatedExecutionStatus.PARTIALLY_FILLED
                && (filledQuantity.signum() == 0 || filledQuantity.compareTo(requestedQuantity) >= 0)) {
            throw new IllegalArgumentException("PARTIALLY_FILLED execution must retain a positive remainder");
        }
        if (status == SimulatedExecutionStatus.UNFILLED && filledQuantity.signum() != 0) {
            throw new IllegalArgumentException("UNFILLED execution cannot contain a fill");
        }
        Objects.requireNonNull(executedAt, "executedAt is required");
        executionModelVersion = required(executionModelVersion, "executionModelVersion");
        sourceFingerprint = required(sourceFingerprint, "sourceFingerprint");
    }

    public BigDecimal unfilledQuantity() {
        return requestedQuantity.subtract(filledQuantity);
    }

    public BigDecimal quantity() {
        return requestedQuantity;
    }

    public BigDecimal fillPrice() {
        return averageExecutionPrice;
    }

    public boolean isFullyFilled() {
        return status == SimulatedExecutionStatus.FILLED;
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
