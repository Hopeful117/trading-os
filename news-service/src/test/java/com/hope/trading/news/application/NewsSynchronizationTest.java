package com.hope.trading.news.application;

import com.hope.trading.news.config.NewsProviderProperties;
import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.EconomicEventStatus;
import com.hope.trading.news.domain.ImpactLevel;
import com.hope.trading.news.infrastructure.xoomar.XoomarEconomicCalendarSourceException;
import com.hope.trading.news.persistence.EconomicEventEntity;
import com.hope.trading.news.persistence.EconomicEventRepository;
import com.hope.trading.news.persistence.FinancialNewsItemEntity;
import com.hope.trading.news.persistence.FinancialNewsItemRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NewsSynchronizationTest {
    @Test
    void fixtureSourceCanPopulateBothNormalizedCatalogs() {
        EconomicEventRepository events = mock(EconomicEventRepository.class);
        FinancialNewsItemRepository news = mock(FinancialNewsItemRepository.class);
        NewsProviderProperties properties = new NewsProviderProperties();
        NewsCatalogService service = new NewsCatalogService(events, news, properties,
                mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(org.springframework.beans.factory.ObjectProvider.class));

        service.synchronize(new FixtureNewsSource(Instant.parse("2026-01-01T12:00:00Z")));

        verify(events).save(any(EconomicEventEntity.class));
        verify(news).save(any(FinancialNewsItemEntity.class));
    }

    @Test
    void doesNotPersistEventsWhenTheProviderFailsBeforeReturningData() {
        EconomicEventRepository events = mock(EconomicEventRepository.class);
        EconomicCalendarSourcePort source = mock(EconomicCalendarSourcePort.class);
        when(source.economicEvents(any(), any())).thenThrow(new RuntimeException("provider unavailable"));
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-02T00:00:00Z");
        NewsCatalogService service = new NewsCatalogService(events,
                mock(FinancialNewsItemRepository.class), new NewsProviderProperties(),
                mock(org.springframework.beans.factory.ObjectProvider.class),
                providerFor(source));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.synchronizeEconomicEvents(source, from, to))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(events);
    }

    @Test
    void doesNotPersistEventsWhenTheProviderTimesOut() {
        EconomicEventRepository events = mock(EconomicEventRepository.class);
        EconomicCalendarSourcePort source = mock(EconomicCalendarSourcePort.class);
        when(source.economicEvents(any(), any()))
                .thenThrow(new XoomarEconomicCalendarSourceException("provider timeout", null));
        NewsCatalogService service = new NewsCatalogService(events,
                mock(FinancialNewsItemRepository.class), new NewsProviderProperties(),
                mock(org.springframework.beans.factory.ObjectProvider.class), providerFor(source));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.synchronizeEconomicEvents(
                        source, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z")))
                .isInstanceOf(XoomarEconomicCalendarSourceException.class)
                .hasMessageContaining("timeout");

        verifyNoInteractions(events);
    }

    @Test
    void repeatedEconomicSynchronizationKeepsIdentityAndUpdatesValues() {
        EconomicEventRepository events = mock(EconomicEventRepository.class);
        EconomicCalendarSourcePort source = mock(EconomicCalendarSourcePort.class);
        Instant scheduledAt = Instant.parse("2026-01-01T12:00:00Z");
        EconomicEvent scheduled = economicEvent(scheduledAt, null);
        EconomicEvent released = economicEvent(scheduledAt, "3.2");
        when(source.economicEvents(any(), any())).thenReturn(List.of(scheduled), List.of(released));

        NewsCatalogService service = new NewsCatalogService(events,
                mock(FinancialNewsItemRepository.class), new NewsProviderProperties(),
                mock(org.springframework.beans.factory.ObjectProvider.class), providerFor(source));
        Instant from = scheduledAt.minusSeconds(3600);
        Instant to = scheduledAt.plusSeconds(3600);

        service.synchronizeEconomicEvents(source, from, to);
        service.synchronizeEconomicEvents(source, from, to);

        var saved = org.mockito.ArgumentCaptor.forClass(EconomicEventEntity.class);
        verify(events, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).toDomain().id())
                .isEqualTo(saved.getAllValues().get(1).toDomain().id());
        assertThat(saved.getAllValues().get(1).toDomain().actualValue()).isEqualTo("3.2");
    }

    private static EconomicEvent economicEvent(Instant scheduledAt, String actualValue) {
        return new EconomicEvent(null, "xoomar", "calendar", "event-1", "CPI", "inflation",
                scheduledAt, null, List.of("USD"), List.of(), ImpactLevel.HIGH,
                actualValue == null ? EconomicEventStatus.SCHEDULED : EconomicEventStatus.RELEASED,
                null, "3.0", actualValue, "%", scheduledAt, scheduledAt, "xoomar-v1");
    }

    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> providerFor(T value) {
        org.springframework.beans.factory.ObjectProvider<T> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
