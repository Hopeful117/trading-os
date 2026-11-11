package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.HistoricalOhlcBackfillApproval;
import com.hope.trading.market_data.model.HistoricalOhlcBackfillPlan;
import com.hope.trading.market_data.model.HistoricalOhlcBackfillWindow;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.OhlcEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class HistoricalOhlcBackfillServiceTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-10-15T10:00:00Z");

    @Test
    void executesSequentiallyOnlyAfterExplicitApprovalAndIsRestartableByIdempotentWriter() {
        MarketHistoryService history = mock(MarketHistoryService.class);
        HistoricalOhlcEvidenceWriter writer = mock(HistoricalOhlcEvidenceWriter.class);
        HistoricalOhlcBackfillService service = new HistoricalOhlcBackfillService(history, writer,
                Clock.fixed(START.plusSeconds(1), ZoneOffset.UTC));
        var first = new HistoricalOhlcBackfillWindow(START, START.plusSeconds(900), 1);
        var second = new HistoricalOhlcBackfillWindow(START.plusSeconds(1800), START.plusSeconds(2700), 1);
        var plan = plan(List.of(first, second));
        when(history.findOhlcHistoryRange(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        when(writer.appendCaptured(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> service.execute(plan, null))
                .isInstanceOf(IllegalArgumentException.class);

        service.execute(plan, approval(plan));

        verify(history).findOhlcHistoryRange(MARKET_ID, OhlcInterval.FIFTEEN_MINUTES,
                first.fromInclusive(), first.toExclusive(), 1);
        verify(history).findOhlcHistoryRange(MARKET_ID, OhlcInterval.FIFTEEN_MINUTES,
                second.fromInclusive(), second.toExclusive(), 1);
        verify(writer, times(2)).appendCaptured(anyList());
    }

    @Test
    void stopsAtFirstFailedWindowSoASecondRunCanResume() {
        MarketHistoryService history = mock(MarketHistoryService.class);
        HistoricalOhlcEvidenceWriter writer = mock(HistoricalOhlcEvidenceWriter.class);
        HistoricalOhlcBackfillService service = new HistoricalOhlcBackfillService(history, writer,
                Clock.fixed(START.plusSeconds(1), ZoneOffset.UTC));
        var first = new HistoricalOhlcBackfillWindow(START, START.plusSeconds(900), 1);
        var second = new HistoricalOhlcBackfillWindow(START.plusSeconds(1800), START.plusSeconds(2700), 1);
        var plan = plan(List.of(first, second));
        when(history.findOhlcHistoryRange(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        when(writer.appendCaptured(anyList())).thenReturn(List.of());
        when(history.findOhlcHistoryRange(MARKET_ID, OhlcInterval.FIFTEEN_MINUTES,
                second.fromInclusive(), second.toExclusive(), 1))
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThatThrownBy(() -> service.execute(plan, approval(plan)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("provider unavailable");

        verify(history, times(1)).findOhlcHistoryRange(MARKET_ID, OhlcInterval.FIFTEEN_MINUTES,
                first.fromInclusive(), first.toExclusive(), 1);
        verify(history, times(1)).findOhlcHistoryRange(MARKET_ID, OhlcInterval.FIFTEEN_MINUTES,
                second.fromInclusive(), second.toExclusive(), 1);
        verify(writer, times(1)).appendCaptured(anyList());
    }

    @Test
    void rejectsApprovalForAnotherPlanAndCannotBeReused() {
        MarketHistoryService history = mock(MarketHistoryService.class);
        HistoricalOhlcEvidenceWriter writer = mock(HistoricalOhlcEvidenceWriter.class);
        HistoricalOhlcBackfillService service = new HistoricalOhlcBackfillService(history, writer,
                Clock.fixed(START.plusSeconds(1), ZoneOffset.UTC));
        HistoricalOhlcBackfillPlan first = plan(List.of(
                new HistoricalOhlcBackfillWindow(START, START.plusSeconds(900), 1)));
        HistoricalOhlcBackfillPlan second = plan(List.of(
                new HistoricalOhlcBackfillWindow(START.plusSeconds(900), START.plusSeconds(1800), 1)));
        when(history.findOhlcHistoryRange(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        when(writer.appendCaptured(anyList())).thenReturn(List.of());
        HistoricalOhlcBackfillApproval approval = approval(first);

        assertThatThrownBy(() -> service.execute(second, approval))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
        service.execute(first, approval);
        assertThatThrownBy(() -> service.execute(first, approval))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been consumed");
    }

    @Test
    void rejectsProviderObservationsOutsideTheApprovedWindowBeforeWriting() {
        MarketHistoryService history = mock(MarketHistoryService.class);
        HistoricalOhlcEvidenceWriter writer = mock(HistoricalOhlcEvidenceWriter.class);
        HistoricalOhlcBackfillService service = new HistoricalOhlcBackfillService(history, writer,
                Clock.fixed(START.plusSeconds(1), ZoneOffset.UTC));
        HistoricalOhlcBackfillPlan plan = plan(List.of(
                new HistoricalOhlcBackfillWindow(START, START.plusSeconds(900), 1)));
        OhlcEvent outsideWindow = new OhlcEvent(MARKET_ID, MarketProvider.CTRADER, "EUR/USD",
                OhlcInterval.FIFTEEN_MINUTES, START.minusSeconds(900), START,
                new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("99"), new BigDecimal("100"),
                BigDecimal.ONE, null, null, true, START, false, "source", START, "1");
        when(history.findOhlcHistoryRange(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(outsideWindow));

        assertThatThrownBy(() -> service.execute(plan, approval(plan)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside the approved window");
        verifyNoInteractions(writer);
    }

    private HistoricalOhlcBackfillPlan plan(List<HistoricalOhlcBackfillWindow> windows) {
        return new HistoricalOhlcBackfillPlan(MARKET_ID, "EUR/USD", MarketProvider.CTRADER,
                OhlcInterval.FIFTEEN_MINUTES, windows, windows.size(), windows.size(), windows.size(),
                "CONFIRMED_MISSING_DATA_ONLY", false);
    }

    private HistoricalOhlcBackfillApproval approval(HistoricalOhlcBackfillPlan plan) {
        return new HistoricalOhlcBackfillApproval("human-review-1", plan.fingerprint(), START,
                START.plusSeconds(3600));
    }
}
