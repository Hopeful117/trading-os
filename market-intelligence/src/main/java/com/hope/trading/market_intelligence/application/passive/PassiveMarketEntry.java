package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;

import java.time.Duration;
import java.util.UUID;

public record PassiveMarketEntry(
        UUID marketId,
        String provider,
        String productType,
        Duration cadence,
        TradeabilityEvidenceProfile minimumEvidenceProfile,
        MarketResponse.MarketStateResponse marketState,
        String skipReason
) {
    public PassiveMarketEntry(UUID marketId, String provider, String productType, Duration cadence,
                              TradeabilityEvidenceProfile minimumEvidenceProfile) {
        this(marketId, provider, productType, cadence, minimumEvidenceProfile, null, null);
    }

    public PassiveMarketEntry(UUID marketId, String provider, String productType, Duration cadence,
                              TradeabilityEvidenceProfile minimumEvidenceProfile,
                              MarketResponse.MarketStateResponse marketState) {
        this(marketId, provider, productType, cadence, minimumEvidenceProfile, marketState, null);
    }
}
