package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HistoricalOhlcSeries(
        UUID marketId,
        OhlcInterval interval,
        Instant requestedAsOf,
        List<HistoricalOhlcEvidence> observations,
        HistoricalOhlcSeriesStatus status,
        String reason
) {
    public HistoricalOhlcSeries {
        observations = observations == null ? List.of() : List.copyOf(observations);
    }

    public enum HistoricalOhlcSeriesStatus {
        AVAILABLE,
        INCOMPLETE
    }
}
