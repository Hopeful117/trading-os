package com.hope.trading.news.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FinancialNewsItem(
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
        List<UUID> marketIds,
        ImpactLevel impact,
        Instant sourceUpdatedAt,
        Instant fetchedAt,
        String normalizationVersion
) {
    public FinancialNewsItem {
        categories = categories == null ? List.of() : List.copyOf(categories);
        currencies = currencies == null ? List.of() : List.copyOf(currencies);
        marketIds = marketIds == null ? List.of() : List.copyOf(marketIds);
        impact = impact == null ? ImpactLevel.UNKNOWN : impact;
        normalizationVersion = normalizationVersion == null ? "v1" : normalizationVersion;
    }
}
