package com.hope.trading.market_intelligence.domain.execution;

import java.time.Duration;
import java.util.Set;

public record RetryPolicy(
        int maximumAttempts,
        Duration backoff,
        Set<RetryClassification> allowedClassifications
) {
    public RetryPolicy {
        if (maximumAttempts < 0 || backoff == null || backoff.isNegative()) {
            throw new IllegalArgumentException("Invalid retry policy");
        }
        allowedClassifications = Set.copyOf(allowedClassifications);
    }

    public boolean permits(RetryClassification classification, int attempts) {
        return attempts < maximumAttempts && allowedClassifications.contains(classification);
    }
}
