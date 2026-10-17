package com.hope.trading.trading_core.execution.domain.economic.research;

import java.util.Objects;

public record StrategyHypothesisIdentity(String hypothesisId, String hypothesisVersion) {
    public StrategyHypothesisIdentity {
        hypothesisId = required(hypothesisId, "hypothesisId");
        hypothesisVersion = required(hypothesisVersion, "hypothesisVersion");
    }

    String fingerprint() {
        return EconomicResearchFingerprint.of(hypothesisId, hypothesisVersion);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
