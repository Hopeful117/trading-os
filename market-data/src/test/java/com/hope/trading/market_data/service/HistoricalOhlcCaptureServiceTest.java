package com.hope.trading.market_data.service;

import com.hope.trading.market_data.config.HistoricalOhlcCaptureProperties;
import com.hope.trading.market_data.model.MarketHistorySnapshot;
import com.hope.trading.market_data.model.OhlcInterval;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoricalOhlcCaptureServiceTest {
    private final MarketHistoryService historyService = mock(MarketHistoryService.class);
    private final HistoricalOhlcEvidenceWriter evidenceWriter = mock(HistoricalOhlcEvidenceWriter.class);
    private final HistoricalOhlcCaptureProperties properties = new HistoricalOhlcCaptureProperties();
    private final HistoricalOhlcCaptureService service =
            new HistoricalOhlcCaptureService(historyService, evidenceWriter, properties);

    @Test
    void skipsCaptureWhenDisabledOrWithoutMarkets() {
        service.capture();
        verifyNoCapture();

        properties.setEnabled(true);
        service.capture();
        verifyNoCapture();
    }

    @Test
    void capturesEachConfiguredMarketAndInterval() {
        UUID marketId = UUID.randomUUID();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(marketId));
        properties.setIntervals(List.of(OhlcInterval.FIFTEEN_MINUTES, OhlcInterval.ONE_HOUR));
        when(historyService.findOhlcHistorySnapshot(eq(marketId), any(OhlcInterval.class), anyInt()))
                .thenReturn(new MarketHistorySnapshot(List.of(), List.of()));

        service.capture();

        verify(historyService).findOhlcHistorySnapshot(marketId, OhlcInterval.FIFTEEN_MINUTES, 200);
        verify(historyService).findOhlcHistorySnapshot(marketId, OhlcInterval.ONE_HOUR, 200);
        verify(evidenceWriter, times(2)).appendCaptured(any(List.class));
    }

    @Test
    void continuesAfterOneCaptureFails() {
        UUID marketId = UUID.randomUUID();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(marketId));
        properties.setIntervals(List.of(OhlcInterval.FIFTEEN_MINUTES, OhlcInterval.ONE_HOUR));
        doThrow(new IllegalStateException("provider unavailable"))
                .when(historyService)
                .findOhlcHistorySnapshot(marketId, OhlcInterval.FIFTEEN_MINUTES, 200);

        service.capture();

        verify(historyService).findOhlcHistorySnapshot(marketId, OhlcInterval.ONE_HOUR, 200);
    }

    @Test
    void rejectsInvalidCaptureLimit() {
        properties.setEnabled(true);
        properties.setMarketIds(List.of(UUID.randomUUID()));
        properties.setLimit(721);

        assertThatThrownBy(service::capture)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 720");
    }

    private void verifyNoCapture() {
        verify(historyService, never()).findOhlcHistorySnapshot(any(), any(), anyInt());
        verify(evidenceWriter, never()).appendCaptured(any(List.class));
    }
}
