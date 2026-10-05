package com.hope.trading.news.application;

import com.hope.trading.news.config.NewsProviderProperties;
import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.EconomicEventStatus;
import com.hope.trading.news.domain.ImpactLevel;
import com.hope.trading.news.domain.NewsIdentity;
import com.hope.trading.news.persistence.EconomicEventEntity;
import com.hope.trading.news.persistence.EconomicEventRepository;
import com.hope.trading.news.persistence.FinancialNewsItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class NewsCatalogServiceTest {
    @Test
    void reportsUnavailableContextWhenNoProductionProviderIsConfigured() {
        NewsProviderProperties properties = new NewsProviderProperties();
        properties.setEnabled(false);
        NewsCatalogService service = new NewsCatalogService(
                mock(EconomicEventRepository.class), mock(FinancialNewsItemRepository.class), properties,
                mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(org.springframework.beans.factory.ObjectProvider.class));

        NewsCatalogService.NewsContextSnapshot context = service.context(UUID.randomUUID(), Instant.now());

        assertThat(context.status().name()).isEqualTo("UNAVAILABLE");
        assertThat(context.events()).isEmpty();
        assertThat(context.news()).isEmpty();
    }

    @Test
    void reportsStaleWhenPersistedFactsExceedTheFreshnessPolicy() {
        EconomicEventRepository events = mock(EconomicEventRepository.class);
        NewsProviderProperties properties = new NewsProviderProperties();
        properties.setEnabled(true);
        properties.setMaxAge(java.time.Duration.ofMinutes(15));
        NewsSourcePort source = mock(NewsSourcePort.class);
        ObjectProvider<NewsSourcePort> sourceProvider = mock(ObjectProvider.class);
        when(sourceProvider.getIfAvailable()).thenReturn(source);
        Instant fetchedAt = Instant.now().minusSeconds(3600);
        EconomicEvent event = new EconomicEvent(
                NewsIdentity.eventId("fixture", "stale"), "fixture", "stale", "CPI", "INFLATION",
                Instant.now(), null, List.of("USD"), List.of(), ImpactLevel.HIGH,
                EconomicEventStatus.SCHEDULED, null, null, null, fetchedAt, fetchedAt, "fixture-v1");
        when(events.findByScheduledAtBetweenOrderByScheduledAtAsc(
                any(), any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(List.of(EconomicEventEntity.from(event)));

        NewsCatalogService service = new NewsCatalogService(events,
                mock(FinancialNewsItemRepository.class), properties, sourceProvider,
                providerFor(mock(EconomicCalendarSourcePort.class)));

        NewsReadResult<EconomicEvent> result = service.findEvents(
                new NewsQuery(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60),
                        null, null, null, 10));

        assertThat(result.status()).isEqualTo(com.hope.trading.news.domain.NewsAvailability.STALE);
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void keepsEconomicContextAvailableWhenOnlyCalendarProviderIsConfigured() {
        EconomicEventRepository events = mock(EconomicEventRepository.class);
        NewsProviderProperties properties = new NewsProviderProperties();
        properties.setEnabled(true);
        Instant scheduledAt = Instant.now().plusSeconds(3600);
        EconomicEvent event = new EconomicEvent(
                NewsIdentity.eventId("xoomar", "event-1"), "xoomar", "event-1", "CPI", "inflation",
                scheduledAt, null, List.of("USD"), List.of(), ImpactLevel.HIGH,
                EconomicEventStatus.SCHEDULED, null, null, null, scheduledAt, Instant.now(), "xoomar-v1");
        when(events.findByScheduledAtBetweenOrderByScheduledAtAsc(
                any(), any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(List.of(EconomicEventEntity.from(event)));

        NewsCatalogService service = new NewsCatalogService(events,
                mock(FinancialNewsItemRepository.class), properties,
                mock(org.springframework.beans.factory.ObjectProvider.class),
                providerFor(mock(EconomicCalendarSourcePort.class)));

        NewsCatalogService.NewsContextSnapshot context = service.context(null, scheduledAt);

        assertThat(context.status()).isEqualTo(com.hope.trading.news.domain.NewsAvailability.AVAILABLE);
        assertThat(context.events()).hasSize(1);
        assertThat(context.news()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> providerFor(T value) {
        org.springframework.beans.factory.ObjectProvider<T> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
