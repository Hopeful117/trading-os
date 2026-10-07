package com.hope.trading.news.api;

import com.hope.trading.news.application.NewsCatalogService;
import com.hope.trading.news.application.NewsReadResult;
import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.NewsAvailability;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsControllerContractTest {
    @Test
    void exposesUnavailableStateInsteadOfAnAmbiguousEmptyList() {
        NewsCatalogService catalog = mock(NewsCatalogService.class);
        when(catalog.findEvents(org.mockito.ArgumentMatchers.any())).thenReturn(
                new NewsReadResult<>(NewsAvailability.UNAVAILABLE, List.of(), Instant.now(),
                        "No production news provider is configured"));
        NewsController controller = new NewsController(catalog);

        NewsController.NewsReadResponse<EconomicEvent> response = controller.events(
                null, null, UUID.randomUUID(), null, null, 10).getBody();

        assertThat(response.status()).isEqualTo("UNAVAILABLE");
        assertThat(response.items()).isEmpty();
        assertThat(response.message()).contains("provider");
        assertThat(response.attribution()).isNull();
    }

    @Test
    void exposesFinancialNewsWithoutEconomicCalendarAttribution() {
        NewsCatalogService catalog = mock(NewsCatalogService.class);
        when(catalog.findNews(org.mockito.ArgumentMatchers.any())).thenReturn(
                new NewsReadResult<>(NewsAvailability.AVAILABLE, List.of(), Instant.now(), null));
        NewsController controller = new NewsController(catalog);

        NewsController.NewsReadResponse<?> response = controller.items(
                null, null, null, null, null, 10).getBody();

        assertThat(response.attribution()).isNull();
    }

    @Test
    void resolvesEconomicAttributionFromNormalizedProviderSource() {
        NewsCatalogService catalog = mock(NewsCatalogService.class);
        EconomicEvent event = new EconomicEvent(UUID.randomUUID(), "xoomar", "BLS", "event-1",
                "CPI", "inflation", Instant.now(), null, List.of("USD"), List.of(),
                com.hope.trading.news.domain.ImpactLevel.HIGH,
                com.hope.trading.news.domain.EconomicEventStatus.SCHEDULED,
                null, null, null, "%", Instant.now(), Instant.now(), "xoomar-v1");
        when(catalog.findEvents(org.mockito.ArgumentMatchers.any())).thenReturn(
                new NewsReadResult<>(NewsAvailability.AVAILABLE, List.of(event), Instant.now(), null));
        com.hope.trading.news.config.NewsProviderProperties properties =
                new com.hope.trading.news.config.NewsProviderProperties();
        properties.setAttributions(java.util.Map.of("xoomar", "Data: XOOMAR"));

        NewsController.NewsReadResponse<EconomicEvent> response = new NewsController(catalog, properties)
                .events(null, null, UUID.randomUUID(), null, null, 10).getBody();

        assertThat(response.attribution()).isEqualTo("Data: XOOMAR");
    }
}
