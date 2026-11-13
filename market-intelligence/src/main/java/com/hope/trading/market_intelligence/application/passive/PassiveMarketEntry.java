package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;

import java.time.Duration;
import java.util.UUID;

public record PassiveMarketEntry(
        UUID marketId,
        String provider,
        String productType,
        Duration cadence,
        TradeabilityEvidenceProfile minimumEvidenceProfile
) {
}
