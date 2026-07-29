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
        Instant fetchedAt = Instant.now();
        ContextSectionStatus status = occurredAt != null
                && occurredAt.isBefore(fetchedAt.minus(staleAfter))
                ? ContextSectionStatus.STALE
                : ContextSectionStatus.AVAILABLE;
        return new ContextSection(
                type,
                status,
                ContextSensitivity.PUBLIC,
                payload,
                new ContextProvenance("market-data", occurredAt, fetchedAt),
                status == ContextSectionStatus.STALE ? "Market data is stale" : null
        );
    }
}
