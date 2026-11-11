package com.hope.trading.market_data.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record HistoricalOhlcContinuityReport(
        UUID marketId,
        String canonicalSymbol,
        OhlcInterval interval,
        Instant fromInclusive,
        Instant toExclusive,
        int expectedObservationCount,
        int validObservationCount,
        Instant latestCompletedCandle,
        List<HistoricalOhlcGapAssessment> assessments
) {
    public HistoricalOhlcContinuityReport {
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(canonicalSymbol, "canonicalSymbol is required");
        Objects.requireNonNull(interval, "interval is required");
        Objects.requireNonNull(fromInclusive, "fromInclusive is required");
        Objects.requireNonNull(toExclusive, "toExclusive is required");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }
        if (expectedObservationCount < 0 || validObservationCount < 0) {
            throw new IllegalArgumentException("Observation counts cannot be negative");
        }
        assessments = List.copyOf(assessments == null ? List.of() : assessments);
    }

    public Map<HistoricalOhlcGapClassification, Long> classificationCounts() {
        return assessments.stream().collect(Collectors.groupingBy(
                HistoricalOhlcGapAssessment::classification, Collectors.counting()));
    }

    public long count(HistoricalOhlcGapClassification classification) {
        return assessments.stream().filter(value -> value.classification() == classification).count();
    }

    public boolean readyFor(Predicate<HistoricalOhlcContinuityReport> featureRequirement) {
        return featureRequirement.test(this)
                && count(HistoricalOhlcGapClassification.MISSING_DATA) == 0
                && count(HistoricalOhlcGapClassification.INVALID_CANDLE) == 0
                && count(HistoricalOhlcGapClassification.DUPLICATE) == 0
                && count(HistoricalOhlcGapClassification.UNCERTAIN) == 0;
    }
}
