package com.hope.trading.market_intelligence.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NewsEventContext(
        UUID id,
        String title,
        String category,
        Instant scheduledAt,
        Instant actualAt,
        List<String> currencies,
        String impact,
        String status,
        String sourceName,
        Instant sourceUpdatedAt,
        Instant fetchedAt,
        String normalizationVersion
) {
    public NewsEventContext {
        currencies = currencies == null ? List.of() : List.copyOf(currencies);
    }
}
