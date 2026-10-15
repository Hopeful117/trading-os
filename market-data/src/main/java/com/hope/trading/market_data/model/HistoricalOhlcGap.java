package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.Objects;

public record HistoricalOhlcGap(Instant openTime, Instant closeTime) {
    public HistoricalOhlcGap {
        Objects.requireNonNull(openTime, "openTime is required");
        Objects.requireNonNull(closeTime, "closeTime is required");
        if (!openTime.isBefore(closeTime)) {
            throw new IllegalArgumentException("openTime must be before closeTime");
        }
    }
}
