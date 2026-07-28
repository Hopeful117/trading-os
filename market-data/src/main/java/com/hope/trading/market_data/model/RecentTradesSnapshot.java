package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecentTradesSnapshot(
        UUID marketId,
        MarketProvider provider,
        String symbol,
        List<TradeEvent> trades,
        Instant generatedAt
) {
    public RecentTradesSnapshot {
        trades = List.copyOf(trades);
    }
}
