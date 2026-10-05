package com.hope.trading.news.application;

import com.hope.trading.news.config.NewsProviderProperties;
import com.hope.trading.news.persistence.EconomicEventEntity;
import com.hope.trading.news.persistence.EconomicEventRepository;
import com.hope.trading.news.persistence.FinancialNewsItemEntity;
import com.hope.trading.news.persistence.FinancialNewsItemRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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

    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> providerFor(T value) {
        org.springframework.beans.factory.ObjectProvider<T> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
