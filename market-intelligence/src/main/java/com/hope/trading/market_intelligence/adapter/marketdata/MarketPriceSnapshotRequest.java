package com.hope.trading.market_intelligence.adapter.marketdata;

import java.util.List;
import java.util.UUID;

public record MarketPriceSnapshotRequest(List<UUID> marketIds) {
    public MarketPriceSnapshotRequest {
        marketIds = List.copyOf(marketIds);
    }
}
