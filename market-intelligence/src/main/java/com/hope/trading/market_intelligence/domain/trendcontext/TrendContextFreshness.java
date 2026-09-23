package com.hope.trading.market_intelligence.domain.trendcontext;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record TrendContextFreshness(
        Duration expectedInterval,
        Instant latestEligibleClosedClose,
        Instant sourceOccurredAt,
        Instant fetchedAt,
        Instant assessmentAt,
        boolean roleAvailable,
        boolean eligibleEvidencePresent
) {
    public TrendContextFreshness {
        Objects.requireNonNull(expectedInterval, "expectedInterval is required");
        Objects.requireNonNull(assessmentAt, "assessmentAt is required");
        if (expectedInterval.isZero() || expectedInterval.isNegative()) {
            throw new IllegalArgumentException("expectedInterval must be positive");
        }
    }
}
