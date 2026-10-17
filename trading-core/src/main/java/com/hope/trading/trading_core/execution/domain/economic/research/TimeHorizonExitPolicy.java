package com.hope.trading.trading_core.execution.domain.economic.research;

import java.time.Duration;
import java.util.Objects;

public record TimeHorizonExitPolicy(String policyVersion, Duration horizon) {
    public TimeHorizonExitPolicy {
        policyVersion = required(policyVersion, "policyVersion");
        if (Objects.requireNonNull(horizon, "horizon is required").isZero() || horizon.isNegative()) {
            throw new IllegalArgumentException("horizon must be positive");
        }
    }

    public java.time.Instant exitAt(java.time.Instant entryAt) {
        return Objects.requireNonNull(entryAt, "entryAt is required").plus(horizon);
    }

    String fingerprint() {
        return EconomicResearchFingerprint.of(policyVersion, horizon.toString());
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
