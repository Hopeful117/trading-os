package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.Objects;

public record HistoricalOhlcGapAssessment(
        Instant openTime,
        Instant closeTime,
        HistoricalOhlcGapClassification classification,
        String reason
) {
    public HistoricalOhlcGapAssessment {
        Objects.requireNonNull(openTime, "openTime is required");
        Objects.requireNonNull(closeTime, "closeTime is required");
        Objects.requireNonNull(classification, "classification is required");
        if (!openTime.isBefore(closeTime)) {
            throw new IllegalArgumentException("openTime must be before closeTime");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }
}
