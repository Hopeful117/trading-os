package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record MarketActivityFact(
        UUID marketId,
        MarketProvider provider,
        String symbol,
        String baseAsset,
        String quoteAsset,
        OhlcInterval interval,
        Duration window,
        Instant windowStart,
        Instant observationBoundary,
        Instant latestEligibleEvidenceTime,
        Duration maxObservationAge,
        BigDecimal quoteNotional,
        int expectedCandleCount,
        int observedCandleCount,
        int eligibleCandleCount,
        int incompleteCandleCount,
        int duplicateCandleCount,
        int conflictingDuplicateCount,
        MarketFactStatus status,
        String reason,
        String calculationVersion
) {
}
