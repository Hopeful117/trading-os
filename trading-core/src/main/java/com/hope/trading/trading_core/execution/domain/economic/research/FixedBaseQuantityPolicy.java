package com.hope.trading.trading_core.execution.domain.economic.research;

import java.math.BigDecimal;
import java.util.Objects;

public record FixedBaseQuantityPolicy(String policyVersion, BigDecimal baseQuantity) {
    public FixedBaseQuantityPolicy {
        policyVersion = required(policyVersion, "policyVersion");
        if (Objects.requireNonNull(baseQuantity, "baseQuantity is required").signum() <= 0) {
            throw new IllegalArgumentException("baseQuantity must be positive");
        }
    }

    String fingerprint() {
        return EconomicResearchFingerprint.of(policyVersion, baseQuantity.toPlainString());
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
