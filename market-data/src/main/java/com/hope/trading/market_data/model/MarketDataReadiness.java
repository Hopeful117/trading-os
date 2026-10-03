package com.hope.trading.market_data.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record MarketDataReadiness(
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
) {
}
