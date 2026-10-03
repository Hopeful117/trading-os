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
                mock(org.springframework.beans.factory.ObjectProvider.class));

        service.synchronize(new FixtureNewsSource(Instant.parse("2026-01-01T12:00:00Z")));

        verify(events).save(any(EconomicEventEntity.class));
        verify(news).save(any(FinancialNewsItemEntity.class));
    }
}
