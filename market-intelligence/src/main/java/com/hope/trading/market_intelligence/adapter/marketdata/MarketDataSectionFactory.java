package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class MarketDataSectionFactory {
    private static final String SOURCE = "market-data";
    private final Duration staleAfter;
    private final HistoricalOhlcFreshnessPolicy historicalOhlcFreshness;
    private final Clock clock;

    public MarketDataSectionFactory(
            @Value("${intelligence.context.stale-after:30s}") Duration staleAfter
    ) {
        this(staleAfter, HistoricalOhlcFreshnessPolicy.DEFAULT_CAPTURE_GRACE, Clock.systemUTC());
    }

    @Autowired
    public MarketDataSectionFactory(
            @Value("${intelligence.context.stale-after:30s}") Duration staleAfter,
            @Value("${intelligence.context.historical-ohlc.capture-grace:30s}") Duration historicalOhlcCaptureGrace,
            Clock clock
    ) {
        this.staleAfter = staleAfter;
        this.historicalOhlcFreshness = new HistoricalOhlcFreshnessPolicy(historicalOhlcCaptureGrace);
        this.clock = clock;
    }

    public MarketDataSectionFactory(Duration staleAfter, Clock clock) {
        this(staleAfter, HistoricalOhlcFreshnessPolicy.DEFAULT_CAPTURE_GRACE, clock);
    }

    public ContextSection available(
            ContextSectionType type,
            ContextPayload payload,
            Instant occurredAt
    ) {
        return available(type, payload, occurredAt, clock.instant());
    }

    public ContextSection available(
            ContextSectionType type,
            ContextPayload payload,
            Instant occurredAt,
            Instant fetchedAt
    ) {
        Instant effectiveFetchedAt = fetchedAt == null ? clock.instant() : fetchedAt;
        ContextSectionStatus status = occurredAt != null
                && occurredAt.isBefore(effectiveFetchedAt.minus(staleAfter))
                ? ContextSectionStatus.STALE
                : ContextSectionStatus.AVAILABLE;
        return new ContextSection(
                type,
                status,
                ContextSensitivity.PUBLIC,
                payload,
                new ContextProvenance(SOURCE, occurredAt, effectiveFetchedAt),
                status == ContextSectionStatus.STALE ? "Market data is stale" : null
        );
    }

    public ContextSection availableAt(
            ContextSectionType type,
            ContextPayload payload,
            Instant occurredAt,
            Instant fetchedAt,
            Instant evaluationCutoff
    ) {
        if (fetchedAt == null) {
            return new ContextSection(
                    type, ContextSectionStatus.INCOMPLETE, ContextSensitivity.PUBLIC, null,
                    new ContextProvenance(SOURCE, occurredAt, null),
                    "Market data capture time is missing");
        }
        if (isFuture(occurredAt, evaluationCutoff)
                || isFuture(fetchedAt, evaluationCutoff)) {
            return new ContextSection(
                    type,
                    ContextSectionStatus.INCOMPLETE,
                    ContextSensitivity.PUBLIC,
                    null,
                    new ContextProvenance(SOURCE, occurredAt, fetchedAt),
                    "Market data is newer than the evaluation cut-off"
            );
        }
        return available(type, payload, occurredAt, fetchedAt);
    }

    public ContextSection historicalOhlcAvailableAt(
            ContextPayload payload,
            String interval,
            Instant latestCompletedCloseTime,
            Instant occurredAt,
            Instant fetchedAt,
            Instant evaluationCutoff
    ) {
        if (evaluationCutoff == null) {
            return available(ContextSectionType.HISTORICAL_OHLC, payload, occurredAt, fetchedAt);
        }
        if (fetchedAt == null) {
            return new ContextSection(
                    ContextSectionType.HISTORICAL_OHLC, ContextSectionStatus.INCOMPLETE,
                    ContextSensitivity.PUBLIC, null,
                    new ContextProvenance(SOURCE, occurredAt, null),
                    "Market data capture time is missing");
        }
        if (isFuture(occurredAt, evaluationCutoff)
                || isFuture(fetchedAt, evaluationCutoff)) {
            return new ContextSection(
                    ContextSectionType.HISTORICAL_OHLC,
                    ContextSectionStatus.INCOMPLETE,
                    ContextSensitivity.PUBLIC,
                    null,
                    new ContextProvenance(SOURCE, occurredAt, fetchedAt),
                    "Market data is newer than the evaluation cut-off");
        }
        ContextSectionStatus status = historicalOhlcFreshness.status(
                interval, latestCompletedCloseTime, evaluationCutoff);
        return new ContextSection(
                ContextSectionType.HISTORICAL_OHLC,
                status,
                ContextSensitivity.PUBLIC,
                payload,
                new ContextProvenance(SOURCE, occurredAt, fetchedAt),
                status == ContextSectionStatus.STALE ? "Market data is stale" : null);
    }

    public ContextSection snapshot(
            ContextPayload payload,
            String snapshotStatus,
            Instant occurredAt,
            Instant fetchedAt
    ) {
        ContextSectionStatus status = "STALE".equals(snapshotStatus)
                ? ContextSectionStatus.STALE
                : ContextSectionStatus.AVAILABLE;
        return new ContextSection(
                ContextSectionType.MARKET_SNAPSHOT,
                status,
                ContextSensitivity.PUBLIC,
                payload,
                new ContextProvenance(
                        SOURCE,
                        occurredAt,
                        fetchedAt == null ? clock.instant() : fetchedAt
                ),
                status == ContextSectionStatus.STALE
                        ? "Current market snapshot is stale"
                        : null
        );
    }

    public ContextSection snapshotAt(
            ContextPayload payload,
            String snapshotStatus,
            Instant occurredAt,
            Instant fetchedAt,
            Instant evaluationCutoff
    ) {
        if (fetchedAt == null) {
            return new ContextSection(
                    ContextSectionType.MARKET_SNAPSHOT, ContextSectionStatus.INCOMPLETE,
                    ContextSensitivity.PUBLIC, null,
                    new ContextProvenance(SOURCE, occurredAt, null),
                    "Market snapshot capture time is missing");
        }
        if (isFuture(occurredAt, evaluationCutoff)
                || isFuture(fetchedAt, evaluationCutoff)) {
            return new ContextSection(
                    ContextSectionType.MARKET_SNAPSHOT,
                    ContextSectionStatus.INCOMPLETE,
                    ContextSensitivity.PUBLIC,
                    null,
                    new ContextProvenance(SOURCE, occurredAt, fetchedAt),
                    "Current market snapshot is newer than the evaluation cut-off"
            );
        }
        return snapshot(payload, snapshotStatus, occurredAt, fetchedAt);
    }

    private boolean isFuture(Instant value, Instant cutoff) {
        return value != null && cutoff != null && value.isAfter(cutoff);
    }
}
