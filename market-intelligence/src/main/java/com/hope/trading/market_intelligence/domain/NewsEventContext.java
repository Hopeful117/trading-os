package com.hope.trading.market_intelligence.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NewsEventContext(
        UUID id,
        String sourceName,
        String source,
        String sourceEventId,
        String title,
        String category,
        Instant scheduledAt,
        Instant actualAt,
        List<String> currencies,
        String impact,
        String status,
        String previousValue,
        String consensusValue,
        String actualValue,
        String unit,
        Instant sourceUpdatedAt,
        Instant fetchedAt,
        String normalizationVersion
) {
    public NewsEventContext {
        currencies = currencies == null ? List.of() : List.copyOf(currencies);
    }
}
