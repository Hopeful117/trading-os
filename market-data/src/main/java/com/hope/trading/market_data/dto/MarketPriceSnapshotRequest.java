package com.hope.trading.market_data.dto;

import java.util.List;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

public record MarketPriceSnapshotRequest(List<UUID> marketIds, Duration maxObservationAge,
                                          Duration acquisitionTimeout, String acquisitionMode,
                                          Instant asOf) {
    public MarketPriceSnapshotRequest(List<UUID> marketIds) {
        this(marketIds, null, null, null, null);
    }

    public MarketPriceSnapshotRequest(List<UUID> marketIds, Duration maxObservationAge,
                                      Duration acquisitionTimeout, String acquisitionMode) {
        this(marketIds, maxObservationAge, acquisitionTimeout, acquisitionMode, null);
    }

    public MarketPriceSnapshotRequest {
        marketIds = marketIds == null ? List.of() : List.copyOf(marketIds);
    }
}
