package com.hope.trading.trading_core.market_data.dto;

import java.util.List;
import java.util.UUID;

public record MarketPriceSnapshotRequest(List<UUID> marketIds) {
    public MarketPriceSnapshotRequest {
        marketIds = List.copyOf(marketIds);
    }
}
