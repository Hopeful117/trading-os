package com.hope.trading.market_intelligence.adapter.marketdata;

import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

public record MarketFactsResponse(
        UUID marketId,
        String symbol,
        String baseAsset,
        String quoteAsset,
        Instant generatedAt,
        MarketActivityResponse activity,
        MarketReadinessResponse readiness
) {
    public enum MarketActivityCalculationBasis {
        VWAP,
        CLOSE_PRICE_FALLBACK,
        NOT_EVALUATED
    }

    public record MarketActivityResponse(
            UUID marketId,
            String provider,
            String symbol,
            String baseAsset,
            String quoteAsset,
            OhlcInterval interval,
            Duration window,
            Instant windowStart,
            Instant observationBoundary,
            Instant latestEligibleEvidenceTime,
            Duration maxObservationAge,
            MarketActivityCalculationBasis calculationBasis,
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
        public MarketActivityResponse(
                UUID marketId,
                String provider,
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
            this(marketId, provider, symbol, baseAsset, quoteAsset, interval, window,
                    windowStart, observationBoundary, latestEligibleEvidenceTime, maxObservationAge,
                    MarketActivityCalculationBasis.VWAP, quoteNotional, expectedCandleCount,
                    observedCandleCount, eligibleCandleCount, incompleteCandleCount,
                    duplicateCandleCount, conflictingDuplicateCount, status, reason, calculationVersion);
        }
    }

    public record MarketReadinessResponse(
            UUID marketId,
            OhlcInterval interval,
            int requestedLookbackCandles,
            int expectedCompletedCandleCount,
            int minimumCompletedCandles,
            int observedCompletedCandles,
            int normalizedCandleCount,
            int syntheticCandleCount,
            int missingIntervalCount,
            int cadenceViolationCount,
            int duplicateCandleCount,
            int conflictingDuplicateCount,
            Instant latestObservedCloseTime,
            Instant latestFetchedAt,
            Instant observationBoundary,
            Duration maxObservationAge,
            MarketFactStatus status,
            String reason,
            String calculationVersion
    ) {}
}
