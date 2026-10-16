package com.hope.trading.market_intelligence.domain.outcome;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record OutcomeHorizon(Duration value) {
    // Market Data currently bounds a 15-minute range request to 720 observations.
    public static final Duration MAXIMUM = Duration.ofMinutes(15L * 720L);

    public OutcomeHorizon {
        Objects.requireNonNull(value, "horizon is required");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("horizon must be positive");
        }
        if (value.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("horizon exceeds the maximum supported duration");
        }
    }

    public static OutcomeHorizon oneHour() {
        return new OutcomeHorizon(Duration.ofHours(1));
    }

    public static OutcomeHorizon fourHours() {
        return new OutcomeHorizon(Duration.ofHours(4));
    }

    public static OutcomeHorizon oneDay() {
        return new OutcomeHorizon(Duration.ofHours(24));
    }

    public Instant maturityAt(Instant evaluationCutoff) {
        return Objects.requireNonNull(evaluationCutoff, "evaluation cutoff is required").plus(value);
    }
}
