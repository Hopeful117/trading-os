package com.hope.trading.market_intelligence.domain;

import java.util.UUID;

public record MarketIdentityContext(
        UUID marketId,
        String provider,
        String symbol,
        String baseAsset,
        String quoteAsset,
        boolean tradable,
        String productType
) implements ContextPayload {
    public MarketIdentityContext(
            UUID marketId, String provider, String symbol, String baseAsset,
            String quoteAsset, boolean tradable) {
        this(marketId, provider, symbol, baseAsset, quoteAsset, tradable, null);
    }
}
