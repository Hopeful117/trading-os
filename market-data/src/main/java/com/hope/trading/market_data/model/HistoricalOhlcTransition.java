package com.hope.trading.market_data.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record HistoricalOhlcTransition(
        Instant previousCloseTime,
        Instant nextOpenTime,
        Duration expectedCadence,
        Duration actualElapsedDuration,
        HistoricalTransitionClassification classification,
        HistoricalClosureEvidence closureEvidence,
        String reason
) {
    public HistoricalOhlcTransition {
        Objects.requireNonNull(previousCloseTime, "previousCloseTime is required");
        Objects.requireNonNull(nextOpenTime, "nextOpenTime is required");
        Objects.requireNonNull(expectedCadence, "expectedCadence is required");
        Objects.requireNonNull(actualElapsedDuration, "actualElapsedDuration is required");
        Objects.requireNonNull(classification, "classification is required");
        if (!previousCloseTime.isBefore(nextOpenTime)) {
            throw new IllegalArgumentException("previousCloseTime must be before nextOpenTime");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        if (classification == HistoricalTransitionClassification.VERIFIED_CLOSURE && closureEvidence == null) {
            throw new IllegalArgumentException("verified closure requires closure evidence");
        }
    }
}
