package com.hope.trading.market_intelligence.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NewsContext(
        UUID marketId,
        List<NewsEventContext> events,
        List<NewsItemContext> news,
        Instant sourceOccurredAt,
        Instant fetchedAt
) implements ContextPayload {
    public NewsContext {
        events = events == null ? List.of() : List.copyOf(events);
        news = news == null ? List.of() : List.copyOf(news);
    }
}
