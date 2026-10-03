package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.UUID;

public record MarketFactsResponse(
        UUID marketId,
        String symbol,
        String baseAsset,
        String quoteAsset,
        Instant generatedAt,
        MarketActivityFact activity,
        MarketDataReadiness readiness
) {
}
