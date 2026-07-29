package com.hope.trading.market_intelligence.adapter.marketdata;

import java.time.Instant;
import java.util.UUID;

public record MarketResponse(
        UUID marketId,
        String provider,
        String symbol,
        String baseAsset,
        String quoteAsset,
        MarketStateResponse marketState
) {
    public record MarketStateResponse(
            String tradingStatus,
            boolean tradable,
            String closureReason,
            Instant lastUpdated
    ) {
    }
}
