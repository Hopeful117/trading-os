package com.hope.trading.market_intelligence.domain;

import java.util.UUID;

public record MarketIdentityContext(
        UUID marketId,
        String provider,
        String symbol,
        String baseAsset,
        String quoteAsset,
        boolean tradable
) implements ContextPayload {
}
