package com.hope.trading.news.persistence;

import com.hope.trading.news.application.NewsQuery;
import com.hope.trading.news.domain.FinancialNewsItem;
import com.hope.trading.news.domain.ImpactLevel;
import com.hope.trading.news.domain.NewsIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "financial_news_items")
public class FinancialNewsItemEntity {
    @Id
    UUID id;
    @Column(name = "source_name", nullable = false, length = 100)
    String sourceName;
    @Column(name = "source_item_id", nullable = false, length = 200)
    String sourceItemId;
    @Column(nullable = false, length = 500)
    String title;
    @Column(columnDefinition = "TEXT")
    String summary;
    @Column(name = "canonical_url", length = 1000)
    String canonicalUrl;
    @Column(name = "published_at", nullable = false)
    Instant publishedAt;
    @Column(length = 200)
    String publisher;
    @Column(columnDefinition = "TEXT")
    String categories;
    @Column(columnDefinition = "TEXT")
    String currencies;
    @Column(name = "market_ids", columnDefinition = "TEXT")
    String marketIds;
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    ImpactLevel impact;
    @Column(name = "source_updated_at")
    Instant sourceUpdatedAt;
    @Column(name = "fetched_at", nullable = false)
    Instant fetchedAt;
    @Column(name = "normalization_version", nullable = false, length = 30)
    String normalizationVersion;

    public static FinancialNewsItemEntity from(FinancialNewsItem item) {
        FinancialNewsItemEntity entity = new FinancialNewsItemEntity();
        entity.id = item.id() == null
                ? NewsIdentity.newsItemId(item.sourceName(), item.sourceItemId()) : item.id();
        entity.sourceName = item.sourceName();
        entity.sourceItemId = item.sourceItemId();
        entity.title = item.title();
        entity.summary = item.summary();
        entity.canonicalUrl = item.canonicalUrl();
        entity.publishedAt = item.publishedAt();
        entity.publisher = item.publisher();
        entity.categories = String.join(",", item.categories());
        entity.currencies = String.join(",", item.currencies());
        entity.marketIds = item.marketIds().stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse("");
        entity.impact = item.impact();
        entity.sourceUpdatedAt = item.sourceUpdatedAt();
        entity.fetchedAt = item.fetchedAt() == null ? Instant.now() : item.fetchedAt();
        entity.normalizationVersion = item.normalizationVersion();
        return entity;
    }

    public FinancialNewsItem toDomain() {
        return new FinancialNewsItem(id, sourceName, sourceItemId, title, summary, canonicalUrl,
                publishedAt, publisher, split(categories), split(currencies), splitUuid(marketIds),
                impact, sourceUpdatedAt, fetchedAt, normalizationVersion);
    }

    public boolean matches(UUID marketId, String currency, ImpactLevel requestedImpact) {
        boolean marketMatches = marketId == null || splitUuid(marketIds).contains(marketId);
        boolean currencyMatches = currency == null || split(currencies).stream()
                .anyMatch(value -> value.equalsIgnoreCase(currency));
        boolean impactMatches = requestedImpact == null || impact == requestedImpact;
        return marketMatches && currencyMatches && impactMatches;
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).toList();
    }

    private static List<UUID> splitUuid(String value) {
        return split(value).stream().map(UUID::fromString).toList();
    }
}
