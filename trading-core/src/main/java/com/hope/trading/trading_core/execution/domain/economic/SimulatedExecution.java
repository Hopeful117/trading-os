package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SimulatedExecution(
        StrategyIntent.Side side,
        BigDecimal requestedQuantity,
        BigDecimal filledQuantity,
        BigDecimal averageExecutionPrice,
        Instant executedAt,
        SimulatedExecutionStatus status,
        String executionModelVersion,
        String sourceFingerprint,
        List<Fill> fills,
        String reason,
        String executionFingerprint
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
                executionModelVersion, sourceFingerprint,
                List.of(new Fill(quantity, fillPrice, executedAt)), null, sourceFingerprint);
    }

    public SimulatedExecution(
            StrategyIntent.Side side,
            BigDecimal requestedQuantity,
            BigDecimal filledQuantity,
            BigDecimal averageExecutionPrice,
            Instant executedAt,
            SimulatedExecutionStatus status,
            String executionModelVersion,
            String sourceFingerprint
    ) {
        this(side, requestedQuantity, filledQuantity, averageExecutionPrice, executedAt, status,
                executionModelVersion, sourceFingerprint,
                filledQuantity.signum() == 0 || averageExecutionPrice == null
                        ? List.of() : List.of(new Fill(filledQuantity, averageExecutionPrice, executedAt)),
                null, sourceFingerprint);
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
        fills = fills == null ? List.of() : List.copyOf(fills);
        if (filledQuantity.signum() == 0 && !fills.isEmpty()) {
            throw new IllegalArgumentException("an unfilled execution cannot contain fills");
        }
        BigDecimal fillTotal = fills.stream().map(Fill::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (fillTotal.compareTo(filledQuantity) != 0) {
            throw new IllegalArgumentException("fill quantities must equal filledQuantity");
        }
        reason = reason == null || reason.isBlank() ? null : reason.trim();
        executionFingerprint = required(executionFingerprint == null ? sourceFingerprint : executionFingerprint,
                "executionFingerprint");
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

    public record Fill(BigDecimal quantity, BigDecimal price, Instant executedAt) {
        public Fill {
            if (Objects.requireNonNull(quantity, "fill quantity is required").signum() <= 0
                    || Objects.requireNonNull(price, "fill price is required").signum() <= 0) {
                throw new IllegalArgumentException("fill quantity and price must be positive");
            }
            Objects.requireNonNull(executedAt, "fill executedAt is required");
        }
    }
}
