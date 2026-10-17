package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.util.Objects;

public record EconomicResult(
        EconomicResultStatus status,
        BigDecimal referenceNotional,
        BigDecimal executionNotional,
        SimulatedExecution execution,
        TransactionCosts costs,
        String calculationFingerprint,
        String reason
) {
    public EconomicResult {
        Objects.requireNonNull(status, "status is required");
        if (status == EconomicResultStatus.AVAILABLE) {
            Objects.requireNonNull(referenceNotional, "referenceNotional is required");
            Objects.requireNonNull(executionNotional, "executionNotional is required");
            Objects.requireNonNull(execution, "execution is required");
            Objects.requireNonNull(costs, "costs are required");
            calculationFingerprint = required(calculationFingerprint, "calculationFingerprint");
        } else if (referenceNotional != null || executionNotional != null || execution != null
                || costs != null || calculationFingerprint != null) {
            throw new IllegalArgumentException("non-AVAILABLE results cannot contain calculated values");
        }
        if (reason != null && reason.isBlank()) reason = null;
    }

    public static EconomicResult unavailable(EconomicResultStatus status, String reason) {
        if (status == EconomicResultStatus.AVAILABLE) {
            throw new IllegalArgumentException("AVAILABLE requires calculated values");
        }
        return new EconomicResult(status, null, null, null, null, null, reason);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
