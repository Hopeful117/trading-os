package com.hope.trading.market_intelligence.adapter.marketdata;

import java.time.Instant;
import java.util.UUID;

public record MarketResponse(
        UUID marketId,
        String provider,
        String symbol,
        String baseAsset,
        String quoteAsset,
        String productType,
        MarketStateResponse marketState
) {
    public MarketResponse(UUID marketId, String provider, String symbol, String baseAsset,
                          String quoteAsset, MarketStateResponse marketState) {
        this(marketId, provider, symbol, baseAsset, quoteAsset, null, marketState);
    }
    public record MarketStateResponse(
            String tradingStatus,
            boolean tradable,
            String closureReason,
            Instant lastUpdated
    ) {
    }
}
