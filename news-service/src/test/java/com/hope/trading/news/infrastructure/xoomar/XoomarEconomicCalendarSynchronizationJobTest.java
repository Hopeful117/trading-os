package com.hope.trading.news.infrastructure.xoomar;

import com.hope.trading.news.application.EconomicCalendarSourcePort;
import com.hope.trading.news.application.NewsCatalogService;
import com.hope.trading.news.config.XoomarProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class XoomarEconomicCalendarSynchronizationJobTest {
    @Test
    void synchronizesTheConfiguredWindow() {
        NewsCatalogService catalog = mock(NewsCatalogService.class);
        EconomicCalendarSourcePort source = mock(EconomicCalendarSourcePort.class);
        XoomarProperties properties = new XoomarProperties();
        XoomarEconomicCalendarSynchronizationJob job =
                new XoomarEconomicCalendarSynchronizationJob(catalog, source, properties);

        job.synchronize();

        verify(catalog).synchronizeEconomicEvents(eq(source), any(Instant.class), any(Instant.class));
    }

    @Test
    void absorbsProviderFailuresForTheScheduledExecution() {
        NewsCatalogService catalog = mock(NewsCatalogService.class);
        EconomicCalendarSourcePort source = mock(EconomicCalendarSourcePort.class);
        doThrow(new XoomarEconomicCalendarSourceException("provider unavailable", null))
                .when(catalog).synchronizeEconomicEvents(eq(source), any(Instant.class), any(Instant.class));
        XoomarEconomicCalendarSynchronizationJob job =
                new XoomarEconomicCalendarSynchronizationJob(catalog, source, new XoomarProperties());

        job.synchronize();

        verify(catalog).synchronizeEconomicEvents(eq(source), any(Instant.class), any(Instant.class));
    }
}
