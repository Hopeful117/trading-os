package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class MarketDataSectionFactory {
    private final Duration staleAfter;

    public MarketDataSectionFactory(
            @Value("${intelligence.context.stale-after:30s}") Duration staleAfter
    ) {
        this.staleAfter = staleAfter;
    }

    public ContextSection available(
            ContextSectionType type,
            ContextPayload payload,
            Instant occurredAt
    ) {
        return available(type, payload, occurredAt, Instant.now());
    }

    public ContextSection available(
            ContextSectionType type,
            ContextPayload payload,
            Instant occurredAt,
            Instant fetchedAt
    ) {
        Instant effectiveFetchedAt = fetchedAt == null ? Instant.now() : fetchedAt;
        ContextSectionStatus status = occurredAt != null
                && occurredAt.isBefore(effectiveFetchedAt.minus(staleAfter))
                ? ContextSectionStatus.STALE
                : ContextSectionStatus.AVAILABLE;
        return new ContextSection(
                type,
                status,
                ContextSensitivity.PUBLIC,
                payload,
                new ContextProvenance("market-data", occurredAt, effectiveFetchedAt),
                status == ContextSectionStatus.STALE ? "Market data is stale" : null
        );
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
                        "market-data",
                        occurredAt,
                        fetchedAt == null ? Instant.now() : fetchedAt
                ),
                status == ContextSectionStatus.STALE
                        ? "Current market snapshot is stale"
                        : null
        );
    }
}
