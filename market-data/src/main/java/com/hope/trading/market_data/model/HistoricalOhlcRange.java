package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HistoricalOhlcRange(
        UUID marketId,
        OhlcInterval interval,
        Instant fromExclusive,
        Instant toInclusive,
        HistoricalOhlcEvidencePolicy evidencePolicy,
        List<HistoricalOhlcEvidence> observations,
        HistoricalOhlcRangeStatus status,
        String reason,
        int expectedObservationCount,
        int observedObservationCount,
        Instant coverageStart,
        Instant coverageEnd,
        List<HistoricalOhlcGap> gaps,
        String evidenceFingerprint,
        List<String> normalizationVersions
) {
    public HistoricalOhlcRange {
        observations = observations == null ? List.of() : List.copyOf(observations);
        gaps = gaps == null ? List.of() : List.copyOf(gaps);
        normalizationVersions = normalizationVersions == null ? List.of() : List.copyOf(normalizationVersions);
        if (expectedObservationCount < 0 || observedObservationCount < 0) {
            throw new IllegalArgumentException("Observation counts cannot be negative");
        }
        if (observedObservationCount != observations.size()) {
            throw new IllegalArgumentException("Observed count must match observations");
        }
    }

    public HistoricalOhlcRange withEvidenceFingerprint(String fingerprint) {
        return new HistoricalOhlcRange(
                marketId, interval, fromExclusive, toInclusive, evidencePolicy,
                observations, status, reason, expectedObservationCount,
                observedObservationCount, coverageStart, coverageEnd, gaps,
                fingerprint, normalizationVersions);
    }
}
