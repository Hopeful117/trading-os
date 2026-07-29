package com.hope.trading.market_intelligence.domain.capability;

import java.time.Duration;
import java.util.Set;

public record RetryPolicy(
        boolean enabled,
        int maxAttempts,
        BackoffStrategy backoffStrategy,
        Duration initialDelay,
        Duration maximumDelay,
        Set<String> retryableFailureTypes
) {
    public RetryPolicy {
        if (maxAttempts < 1 || initialDelay == null || initialDelay.isNegative()
                || maximumDelay == null || maximumDelay.isNegative()
                || initialDelay.compareTo(maximumDelay) > 0) {
            throw new IllegalArgumentException("Invalid retry policy");
        }
        retryableFailureTypes = Set.copyOf(retryableFailureTypes);
    }
    public static RetryPolicy disabled() {
        return new RetryPolicy(false, 1, BackoffStrategy.FIXED,
                Duration.ZERO, Duration.ZERO, Set.of());
    }
}
