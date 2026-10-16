package com.hope.trading.market_intelligence.domain.outcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record OutcomeFutureObservation(
        Instant openTime,
        Instant closeTime,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        String observationFingerprint
) {
    public OutcomeFutureObservation {
        Objects.requireNonNull(openTime, "future open time is required");
        Objects.requireNonNull(closeTime, "future close time is required");
        Objects.requireNonNull(high, "future high is required");
        Objects.requireNonNull(low, "future low is required");
        Objects.requireNonNull(close, "future close is required");
        Objects.requireNonNull(observationFingerprint, "future fingerprint is required");
        if (!openTime.isBefore(closeTime)) {
            throw new IllegalArgumentException("future open time must precede close time");
        }
        if (low.signum() <= 0 || high.signum() <= 0 || close.signum() <= 0
                || low.compareTo(high) > 0 || close.compareTo(low) < 0 || close.compareTo(high) > 0) {
            throw new IllegalArgumentException("future OHLC values are invalid");
        }
    }
}
