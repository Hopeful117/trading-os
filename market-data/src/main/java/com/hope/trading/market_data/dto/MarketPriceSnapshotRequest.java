package com.hope.trading.market_data.dto;

import java.util.List;
import java.util.UUID;

public record MarketPriceSnapshotRequest(List<UUID> marketIds) {
    public MarketPriceSnapshotRequest {
        marketIds = marketIds == null ? List.of() : List.copyOf(marketIds);
    }
}
