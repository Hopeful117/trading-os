package com.hope.trading.trading_core.market_data.dto;

import java.util.List;
import java.util.UUID;
import java.time.Duration;

public record MarketPriceSnapshotRequest(List<UUID> marketIds, Duration maxObservationAge,
                                         Duration acquisitionTimeout, String acquisitionMode) {
    public MarketPriceSnapshotRequest(List<UUID> marketIds) {
        this(marketIds, null, null, null);
    }

    public MarketPriceSnapshotRequest {
        marketIds = List.copyOf(marketIds);
    }
}
