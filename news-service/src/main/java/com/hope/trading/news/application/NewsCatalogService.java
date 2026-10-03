package com.hope.trading.news.application;

import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.FinancialNewsItem;
import com.hope.trading.news.domain.NewsAvailability;
import com.hope.trading.news.config.NewsProviderProperties;
import com.hope.trading.news.persistence.EconomicEventEntity;
import com.hope.trading.news.persistence.EconomicEventRepository;
import com.hope.trading.news.persistence.FinancialNewsItemEntity;
import com.hope.trading.news.persistence.FinancialNewsItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class NewsCatalogService {
    private final EconomicEventRepository events;
    private final FinancialNewsItemRepository news;
    private final NewsProviderProperties providerProperties;
    private final ObjectProvider<NewsSourcePort> source;

    public NewsCatalogService(EconomicEventRepository events,
                              FinancialNewsItemRepository news,
                              NewsProviderProperties providerProperties,
                              ObjectProvider<NewsSourcePort> source) {
        this.events = events;
        this.news = news;
        this.providerProperties = providerProperties;
        this.source = source;
    }

    @Transactional(readOnly = true)
    public NewsReadResult<EconomicEvent> findEvents(NewsQuery query) {
        if (!isProviderAvailable()) {
            return unavailable();
        }
        Instant from = query.from() == null ? Instant.now().minusSeconds(86400) : query.from();
        Instant to = query.to() == null ? Instant.now().plusSeconds(172800) : query.to();
        List<EconomicEventEntity> entities = events.findByScheduledAtBetweenOrderByScheduledAtAsc(
                        from, to, query.marketId() == null ? null : query.marketId().toString(),
                        query.currency(), query.impact(), PageRequest.of(0, query.limit())).stream()
                .filter(entity -> entity.matches(query.marketId(), query.currency(), null))
                .toList();
        List<EconomicEvent> domainItems = entities.stream().map(EconomicEventEntity::toDomain).toList();
        NewsAvailability status = availability(domainItems);
        return new NewsReadResult<>(status, domainItems, Instant.now(), freshnessMessage(status));
    }

    @Transactional(readOnly = true)
    public NewsReadResult<FinancialNewsItem> findNews(NewsQuery query) {
        if (!isProviderAvailable()) {
            return unavailable();
        }
        Instant from = query.from() == null ? Instant.now().minusSeconds(172800) : query.from();
        Instant to = query.to() == null ? Instant.now() : query.to();
        List<FinancialNewsItemEntity> entities = news.findByPublishedAtBetweenOrderByPublishedAtDesc(
                        from, to, query.marketId() == null ? null : query.marketId().toString(),
                        query.currency(), query.impact(), PageRequest.of(0, query.limit())).stream()
                .filter(entity -> entity.matches(query.marketId(), query.currency(), null))
                .toList();
        List<FinancialNewsItem> domainItems = entities.stream().map(FinancialNewsItemEntity::toDomain).toList();
        NewsAvailability status = availability(domainItems);
        return new NewsReadResult<>(status, domainItems, Instant.now(), freshnessMessage(status));
    }

    @Transactional
    public void synchronize(NewsSourcePort source) {
        source.economicEvents().forEach(event -> events.save(EconomicEventEntity.from(event)));
        source.financialNews().forEach(item -> news.save(FinancialNewsItemEntity.from(item)));
    }

    public NewsContextSnapshot context(UUID marketId, Instant now) {
        if (!isProviderAvailable()) {
            return new NewsContextSnapshot(NewsAvailability.UNAVAILABLE, List.of(), List.of(), null, now,
                    "No production news provider is configured");
        }
        NewsQuery eventQuery = new NewsQuery(now.minusSeconds(86400), now.plusSeconds(172800),
                marketId, null, null, 100);
        NewsQuery newsQuery = new NewsQuery(now.minusSeconds(172800), now, marketId,
                null, null, 100);
        NewsReadResult<EconomicEvent> eventsResult = findEvents(eventQuery);
        NewsReadResult<FinancialNewsItem> newsResult = findNews(newsQuery);
        NewsAvailability status = eventsResult.status() == NewsAvailability.STALE
                || newsResult.status() == NewsAvailability.STALE
                ? NewsAvailability.STALE : NewsAvailability.AVAILABLE;
        return new NewsContextSnapshot(status, eventsResult.items(),
                newsResult.items(), sourceOccurredAt(eventsResult.items(), newsResult.items()), now,
                freshnessMessage(status));
    }

    private boolean isProviderAvailable() {
        return providerProperties.enabled() && source.getIfAvailable() != null;
    }

    private <T> NewsReadResult<T> unavailable() {
        return new NewsReadResult<>(NewsAvailability.UNAVAILABLE, List.of(), Instant.now(),
                "No production news provider is configured");
    }

    private NewsAvailability availability(List<?> items) {
        Instant now = Instant.now();
        boolean stale = items.stream()
                .map(item -> item instanceof EconomicEvent event ? event.fetchedAt()
                        : ((FinancialNewsItem) item).fetchedAt())
                .filter(java.util.Objects::nonNull)
                .anyMatch(fetchedAt -> fetchedAt.plus(providerProperties.maxAge()).isBefore(now));
        return stale ? NewsAvailability.STALE : NewsAvailability.AVAILABLE;
    }

    private String freshnessMessage(NewsAvailability status) {
        return status == NewsAvailability.STALE
                ? "News data exceeds the configured freshness policy" : null;
    }

    private Instant sourceOccurredAt(List<EconomicEvent> events, List<FinancialNewsItem> news) {
        return java.util.stream.Stream.concat(
                        events.stream().map(event -> event.sourceUpdatedAt() == null
                                ? event.scheduledAt() : event.sourceUpdatedAt()),
                        news.stream().map(item -> item.sourceUpdatedAt() == null
                                ? item.publishedAt() : item.sourceUpdatedAt()))
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    public record NewsContextSnapshot(NewsAvailability status,
                                      List<EconomicEvent> events,
                                      List<FinancialNewsItem> news,
                                      Instant sourceOccurredAt,
                                      Instant fetchedAt,
                                      String message) {
    }
}
