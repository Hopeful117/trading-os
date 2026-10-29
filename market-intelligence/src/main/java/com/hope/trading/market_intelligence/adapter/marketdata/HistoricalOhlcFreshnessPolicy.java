package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.ContextSectionStatus;

import java.time.Duration;
import java.time.Instant;

/** Applies completed-candle freshness semantics to strict historical OHLC. */
final class HistoricalOhlcFreshnessPolicy {
    static final Duration DEFAULT_CAPTURE_GRACE = Duration.ofSeconds(30);

    private final Duration captureGrace;

    HistoricalOhlcFreshnessPolicy(Duration captureGrace) {
        if (captureGrace == null || captureGrace.isNegative()) {
            throw new IllegalArgumentException("OHLC capture grace must be non-negative");
        }
        this.captureGrace = captureGrace;
    }

    ContextSectionStatus status(String interval, Instant latestCompletedCloseTime, Instant evaluationCutoff) {
        if (latestCompletedCloseTime == null || evaluationCutoff == null) {
            return ContextSectionStatus.INCOMPLETE;
        }
        Duration intervalDuration = intervalDuration(interval);
        Instant latestExpected = latestCompletedBoundary(evaluationCutoff, intervalDuration);
        Instant oldestAccepted = latestExpected.minus(intervalDuration).minus(captureGrace);
        return latestCompletedCloseTime.isBefore(oldestAccepted)
                ? ContextSectionStatus.STALE
                : ContextSectionStatus.AVAILABLE;
    }

    private Instant latestCompletedBoundary(Instant evaluation, Duration interval) {
        long intervalSeconds = interval.toSeconds();
        long boundary = Math.floorDiv(evaluation.getEpochSecond(), intervalSeconds) * intervalSeconds;
        return Instant.ofEpochSecond(boundary);
    }

    private Duration intervalDuration(String interval) {
        return switch (OhlcInterval.valueOf(interval)) {
            case ONE_MINUTE -> Duration.ofMinutes(1);
            case FIVE_MINUTES -> Duration.ofMinutes(5);
            case FIFTEEN_MINUTES -> Duration.ofMinutes(15);
            case THIRTY_MINUTES -> Duration.ofMinutes(30);
            case ONE_HOUR -> Duration.ofHours(1);
            case FOUR_HOURS -> Duration.ofHours(4);
            case ONE_DAY -> Duration.ofDays(1);
            default -> throw new IllegalArgumentException("Unsupported OHLC interval: " + interval);
        };
    }
}
