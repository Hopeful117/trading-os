package com.hope.trading.market_intelligence.domain.execution;

import java.time.Instant;
import java.util.Optional;

public record RetryMetadata(
        int attempts,
        int maximumAttempts,
        RetryClassification lastClassification,
        Instant lastAttemptAt,
        String lastErrorCode
) {
    public RetryMetadata {
        if (attempts < 0 || maximumAttempts < 0 || attempts > maximumAttempts) {
            throw new IllegalArgumentException("Invalid retry attempt count");
        }
    }

    public static RetryMetadata none(int maximumAttempts) {
        return new RetryMetadata(0, maximumAttempts, null, null, null);
    }

    public boolean canRetry() {
        return attempts < maximumAttempts
                && lastClassification == RetryClassification.RETRYABLE;
    }

    public Optional<RetryClassification> classification() {
        return Optional.ofNullable(lastClassification);
    }
}
