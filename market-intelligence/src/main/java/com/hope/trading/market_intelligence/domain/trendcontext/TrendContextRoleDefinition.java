package com.hope.trading.market_intelligence.domain.trendcontext;

import java.time.Duration;
import java.util.Objects;

public record TrendContextRoleDefinition(
        TrendContextRole role,
        String interval,
        Duration intervalDuration,
        boolean required,
        int minimumEligibleCandles,
        int requestedCandles
) {
    public TrendContextRoleDefinition {
        Objects.requireNonNull(role, "role is required");
        interval = requireText(interval, "interval");
        Objects.requireNonNull(intervalDuration, "intervalDuration is required");
        if (intervalDuration.isZero() || intervalDuration.isNegative()) {
            throw new IllegalArgumentException("intervalDuration must be positive");
        }
        if (minimumEligibleCandles < 1) {
            throw new IllegalArgumentException("minimumEligibleCandles must be positive");
        }
        if (requestedCandles < minimumEligibleCandles) {
            throw new IllegalArgumentException(
                    "requestedCandles must cover minimumEligibleCandles");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim().toUpperCase();
    }
}
