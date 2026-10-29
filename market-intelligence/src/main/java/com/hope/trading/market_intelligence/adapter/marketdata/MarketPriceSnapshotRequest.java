package com.hope.trading.market_intelligence.adapter.marketdata;

import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record MarketPriceSnapshotRequest(
        List<UUID> marketIds,
        Duration maxObservationAge,
        Duration acquisitionTimeout,
        String acquisitionMode,
        Instant asOf
) {
    public MarketPriceSnapshotRequest(List<UUID> marketIds) {
        this(marketIds, null, null, null, null);
    }

    public MarketPriceSnapshotRequest(List<UUID> marketIds, Duration maxObservationAge,
                                      Duration acquisitionTimeout, String acquisitionMode) {
        this(marketIds, maxObservationAge, acquisitionTimeout, acquisitionMode, null);
    }

    public MarketPriceSnapshotRequest {
        marketIds = List.copyOf(marketIds);
    }
}
