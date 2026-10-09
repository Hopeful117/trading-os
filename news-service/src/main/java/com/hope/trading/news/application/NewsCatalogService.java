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
    private final ObjectProvider<EconomicCalendarSourcePort> economicCalendarSource;

    public NewsCatalogService(EconomicEventRepository events,
                              FinancialNewsItemRepository news,
                              NewsProviderProperties providerProperties,
                              ObjectProvider<NewsSourcePort> source,
                              ObjectProvider<EconomicCalendarSourcePort> economicCalendarSource) {
        this.events = events;
        this.news = news;
        this.providerProperties = providerProperties;
        this.source = source;
        this.economicCalendarSource = economicCalendarSource;
    }

    @Transactional(readOnly = true)
    public NewsReadResult<EconomicEvent> findEvents(NewsQuery query) {
        EconomicCalendarSourcePort provider = economicCalendarSource.getIfAvailable();
        if (provider == null) {
            return unavailable();
        }
        NewsAvailability providerStatus = provider.availability();
        if (providerStatus != null && providerStatus != NewsAvailability.AVAILABLE) {
            return unavailable(providerStatus);
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
        NewsSourcePort provider = isProviderAvailable() ? source.getIfAvailable() : null;
        if (provider == null) {
            return unavailable();
        }
        NewsAvailability providerStatus = provider.availability();
        if (providerStatus != null && providerStatus != NewsAvailability.AVAILABLE) {
            return unavailable(providerStatus);
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

    @Transactional
    public void synchronizeEconomicEvents(EconomicCalendarSourcePort source, Instant from, Instant to) {
        source.economicEvents(from, to).forEach(event -> events.save(EconomicEventEntity.from(event)));
    }

    public NewsContextSnapshot context(UUID marketId, Instant now) {
        if (!isEconomicCalendarAvailable() && !isProviderAvailable()) {
            return new NewsContextSnapshot(NewsAvailability.UNAVAILABLE, List.of(), List.of(), null, now,
                    "No production news provider is configured");
        }
        NewsQuery eventQuery = new NewsQuery(now.minusSeconds(86400), now.plusSeconds(172800),
                marketId, null, null, 100);
        NewsQuery newsQuery = new NewsQuery(now.minusSeconds(172800), now, marketId,
                null, null, 100);
        NewsReadResult<EconomicEvent> eventsResult = findEvents(eventQuery);
        NewsReadResult<FinancialNewsItem> newsResult = findNews(newsQuery);
        NewsAvailability status = contextAvailability(eventsResult.status(), newsResult.status());
        return new NewsContextSnapshot(status, eventsResult.items(),
                newsResult.items(), sourceOccurredAt(eventsResult.items(), newsResult.items()), now,
                freshnessMessage(status));
    }

    private boolean isProviderAvailable() {
        return providerProperties.enabled() && source.getIfAvailable() != null;
    }

    private boolean isEconomicCalendarAvailable() {
        return economicCalendarSource.getIfAvailable() != null;
    }

    private NewsAvailability contextAvailability(NewsAvailability eventsStatus,
                                                  NewsAvailability newsStatus) {
        if (eventsStatus == NewsAvailability.INCOMPLETE || newsStatus == NewsAvailability.INCOMPLETE) {
            return NewsAvailability.INCOMPLETE;
        }
        if (eventsStatus == NewsAvailability.UNSUPPORTED && newsStatus == NewsAvailability.UNSUPPORTED) {
            return NewsAvailability.UNSUPPORTED;
        }
        if (eventsStatus == NewsAvailability.UNSUPPORTED || newsStatus == NewsAvailability.UNSUPPORTED) {
            return NewsAvailability.INCOMPLETE;
        }
        if (eventsStatus == NewsAvailability.STALE || newsStatus == NewsAvailability.STALE) {
            return NewsAvailability.STALE;
        }
        if (eventsStatus == NewsAvailability.AVAILABLE || newsStatus == NewsAvailability.AVAILABLE) {
            return NewsAvailability.AVAILABLE;
        }
        return NewsAvailability.UNAVAILABLE;
    }

    private <T> NewsReadResult<T> unavailable() {
        return unavailable(NewsAvailability.UNAVAILABLE);
    }

    private <T> NewsReadResult<T> unavailable(NewsAvailability status) {
        return new NewsReadResult<>(status, List.of(), Instant.now(),
                statusMessage(status));
    }

    private String statusMessage(NewsAvailability status) {
        return switch (status) {
            case UNSUPPORTED -> "The configured news provider is unsupported";
            case INCOMPLETE -> "The configured news provider returned incomplete data";
            case UNAVAILABLE -> "No production news provider is configured";
            case STALE -> "News data exceeds the configured freshness policy";
            case AVAILABLE -> null;
        };
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
