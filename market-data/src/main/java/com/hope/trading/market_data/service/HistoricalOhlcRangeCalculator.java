package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.HistoricalOhlcEvidence;
import com.hope.trading.market_data.model.HistoricalOhlcEvidenceOrigin;
import com.hope.trading.market_data.model.HistoricalOhlcEvidencePolicy;
import com.hope.trading.market_data.model.HistoricalOhlcGap;
import com.hope.trading.market_data.model.HistoricalOhlcRange;
import com.hope.trading.market_data.model.HistoricalOhlcRangeStatus;
import com.hope.trading.market_data.model.OhlcInterval;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HistoricalOhlcRangeCalculator {
    public static final int MAX_EXPECTED_OBSERVATIONS = 720;

    private HistoricalOhlcRangeCalculator() {
    }

    public static void validateRequest(
            UUID marketId,
            OhlcInterval interval,
            Instant fromExclusive,
            Instant toInclusive,
            HistoricalOhlcEvidencePolicy policy
    ) {
        if (marketId == null || interval == null || fromExclusive == null || toInclusive == null || policy == null) {
            throw new IllegalArgumentException("marketId, interval, boundaries and evidence policy are required");
        }
        if (policy != HistoricalOhlcEvidencePolicy.CAPTURED) {
            throw new IllegalArgumentException("Unsupported historical OHLC range evidence policy: " + policy);
        }
        if (!fromExclusive.isBefore(toInclusive)) {
            throw new IllegalArgumentException("fromExclusive must be before toInclusive");
        }
        expectedOpenTimes(interval, fromExclusive, toInclusive);
    }

    public static HistoricalOhlcRange calculate(
            UUID marketId,
            OhlcInterval interval,
            Instant fromExclusive,
            Instant toInclusive,
            HistoricalOhlcEvidencePolicy policy,
            List<HistoricalOhlcEvidence> candidates
    ) {
        validateRequest(marketId, interval, fromExclusive, toInclusive, policy);
        List<Instant> expectedOpenTimes = expectedOpenTimes(interval, fromExclusive, toInclusive);
        Map<Instant, HistoricalOhlcEvidence> byOpenTime = new HashMap<>();
        candidates.stream()
                .filter(evidence -> evidence.marketId().equals(marketId))
                .filter(evidence -> evidence.interval() == interval)
                .filter(evidence -> evidence.closed() && !evidence.synthetic())
                .filter(evidence -> evidence.origin() == HistoricalOhlcEvidenceOrigin.CAPTURED)
                .filter(evidence -> !evidence.openTime().isBefore(fromExclusive))
                .filter(evidence -> !evidence.closeTime().isAfter(toInclusive))
                .sorted(Comparator.comparing(HistoricalOhlcEvidence::knownAt).reversed()
                        .thenComparing(Comparator.comparing(HistoricalOhlcEvidence::capturedAt).reversed())
                        .thenComparing(Comparator.comparing(HistoricalOhlcEvidence::observationId).reversed()))
                .forEach(evidence -> byOpenTime.putIfAbsent(evidence.openTime(), evidence));

        List<HistoricalOhlcEvidence> observations = new ArrayList<>();
        List<HistoricalOhlcGap> gaps = new ArrayList<>();
        for (Instant openTime : expectedOpenTimes) {
            HistoricalOhlcEvidence evidence = byOpenTime.get(openTime);
            if (evidence == null) {
                gaps.add(new HistoricalOhlcGap(openTime, openTime.plus(interval.getDuration())));
            } else {
                observations.add(evidence);
            }
        }
        observations.sort(Comparator.comparing(HistoricalOhlcEvidence::openTime));

        HistoricalOhlcRangeStatus status;
        String reason;
        if (expectedOpenTimes.isEmpty()) {
            status = HistoricalOhlcRangeStatus.UNAVAILABLE;
            reason = "NO_COMPLETE_INTERVAL";
        } else if (observations.isEmpty()) {
            status = HistoricalOhlcRangeStatus.UNAVAILABLE;
            reason = "NO_CAPTURED_EVIDENCE";
        } else if (!gaps.isEmpty()) {
            status = HistoricalOhlcRangeStatus.INCOMPLETE;
            reason = "MISSING_EXPECTED_INTERVALS";
        } else {
            status = HistoricalOhlcRangeStatus.AVAILABLE;
            reason = null;
        }

        List<String> normalizationVersions = observations.stream()
                .map(HistoricalOhlcEvidence::normalizationVersion)
                .distinct()
                .sorted()
                .toList();
        HistoricalOhlcRange range = new HistoricalOhlcRange(
                marketId,
                interval,
                fromExclusive,
                toInclusive,
                policy,
                observations,
                status,
                reason,
                expectedOpenTimes.size(),
                observations.size(),
                observations.isEmpty() ? null : observations.getFirst().openTime(),
                observations.isEmpty() ? null : observations.getLast().closeTime(),
                gaps,
                "pending",
                normalizationVersions);
        return range.withEvidenceFingerprint(HistoricalOhlcRangeFingerprint.fingerprint(range));
    }

    private static List<Instant> expectedOpenTimes(
            OhlcInterval interval,
            Instant fromExclusive,
            Instant toInclusive
    ) {
        Duration duration = interval.getDuration();
        long seconds = duration.getSeconds();
        long alignedEpochSecond = Math.floorDiv(fromExclusive.getEpochSecond(), seconds) * seconds;
        Instant openTime = Instant.ofEpochSecond(alignedEpochSecond);
        if (openTime.isBefore(fromExclusive)) {
            openTime = openTime.plusSeconds(seconds);
        }

        List<Instant> expected = new ArrayList<>();
        while (!openTime.plus(duration).isAfter(toInclusive)) {
            if (expected.size() == MAX_EXPECTED_OBSERVATIONS) {
                throw new IllegalArgumentException("Historical OHLC range exceeds "
                        + MAX_EXPECTED_OBSERVATIONS + " expected observations");
            }
            expected.add(openTime);
            openTime = openTime.plusSeconds(seconds);
        }
        return expected;
    }
}
