package com.hope.trading.market_data.scheduler;

import com.hope.trading.market_data.service.HistoricalOhlcCaptureService;
import com.hope.trading.market_data.service.MarketSynchronization;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StartupSchedulerConfigurationTest {
    @Test
    void historicalCaptureKeepsItsCadenceAndWaitsForStartupGrace() throws NoSuchMethodException {
        Scheduled scheduled = HistoricalOhlcCaptureScheduler.class
                .getMethod("capture")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString()).isEqualTo("${historical-ohlc.capture.fixed-delay:PT15M}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${market-data.startup-grace:PT1M}");

        HistoricalOhlcCaptureService captureService = mock(HistoricalOhlcCaptureService.class);
        new HistoricalOhlcCaptureScheduler(captureService).capture();
        verify(captureService).capture();
    }

    @Test
    void marketSynchronizationKeepsItsCadenceAndWaitsForStartupGrace() throws NoSuchMethodException {
        Scheduled scheduled = MarketSynchronizationScheduler.class
                .getMethod("synchronizeMarkets")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString()).isEqualTo("${market-synchronization.fixed-delay:PT1H}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${market-data.startup-grace:PT1M}");

        MarketSynchronization synchronization = mock(MarketSynchronization.class);
        new MarketSynchronizationScheduler(synchronization).synchronizeMarkets();
        verify(synchronization).synchronizeMarkets();
    }
}
