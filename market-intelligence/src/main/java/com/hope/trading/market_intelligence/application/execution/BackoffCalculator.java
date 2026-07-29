package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.domain.capability.*;

import java.time.Duration;

public class BackoffCalculator {
    public Duration delay(RetryPolicy policy, int completedAttempt) {
        long multiplier = switch (policy.backoffStrategy()) {
            case FIXED -> 1;
            case LINEAR -> completedAttempt;
            case EXPONENTIAL -> 1L << Math.min(completedAttempt - 1, 30);
        };
        Duration computed = policy.initialDelay().multipliedBy(multiplier);
        return computed.compareTo(policy.maximumDelay()) > 0
                ? policy.maximumDelay() : computed;
    }
}
