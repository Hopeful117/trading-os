package com.hope.trading.market_intelligence.adapter.marketdata;

import java.util.List;
import java.time.Duration;
import java.util.UUID;

public record MarketPriceSnapshotRequest(
        List<UUID> marketIds,
        Duration maxObservationAge,
        Duration acquisitionTimeout,
        String acquisitionMode
) {
    public MarketPriceSnapshotRequest(List<UUID> marketIds) {
        this(marketIds, null, null, null);
    }

    public MarketPriceSnapshotRequest {
        marketIds = List.copyOf(marketIds);
    }
}
