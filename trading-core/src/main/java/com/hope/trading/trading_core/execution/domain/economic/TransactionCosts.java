package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.util.Objects;

public record TransactionCosts(
        BigDecimal executionImpact,
        BigDecimal fee,
        BigDecimal funding,
        BigDecimal total,
        CostEvidence feeEvidence,
        CostEvidence fundingEvidence
) {
    public TransactionCosts {
        requireNonNegative(executionImpact, "executionImpact");
        requireNonNegative(fee, "fee");
        Objects.requireNonNull(funding, "funding is required");
        Objects.requireNonNull(total, "total is required");
        Objects.requireNonNull(feeEvidence, "feeEvidence is required");
        Objects.requireNonNull(fundingEvidence, "fundingEvidence is required");
        BigDecimal expected = executionImpact.add(fee).add(funding);
        if (total.compareTo(expected) != 0) {
            throw new IllegalArgumentException("total must equal executionImpact + fee + funding");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (Objects.requireNonNull(value, field).signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }
}
