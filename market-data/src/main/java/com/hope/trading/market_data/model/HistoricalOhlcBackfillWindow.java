package com.hope.trading.market_data.model;

import java.time.Instant;

public record HistoricalOhlcBackfillWindow(
        Instant fromInclusive,
        Instant toExclusive,
        int estimatedCandleCount
) {
    public HistoricalOhlcBackfillWindow {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("Backfill window bounds are invalid");
        }
        if (estimatedCandleCount < 1) {
            throw new IllegalArgumentException("Backfill window must contain candles");
        }
    }
}
