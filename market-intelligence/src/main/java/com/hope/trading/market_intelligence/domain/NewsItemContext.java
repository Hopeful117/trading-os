package com.hope.trading.market_intelligence.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NewsItemContext(
        UUID id,
        String sourceName,
        String sourceItemId,
        String title,
        String summary,
        String canonicalUrl,
        Instant publishedAt,
        String publisher,
        List<String> categories,
        List<String> currencies,
        String impact,
        Instant sourceUpdatedAt,
        Instant fetchedAt,
        String normalizationVersion
) {
    public NewsItemContext {
        categories = categories == null ? List.of() : List.copyOf(categories);
        currencies = currencies == null ? List.of() : List.copyOf(currencies);
    }
}
