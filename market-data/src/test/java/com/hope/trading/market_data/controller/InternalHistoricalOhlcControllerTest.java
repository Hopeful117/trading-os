package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.model.HistoricalOhlcSeries;
import com.hope.trading.market_data.model.HistoricalOhlcRange;
import com.hope.trading.market_data.model.HistoricalOhlcRangeStatus;
import com.hope.trading.market_data.model.HistoricalOhlcEvidencePolicy;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.service.HistoricalOhlcHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalHistoricalOhlcControllerTest {
    private final HistoricalOhlcHistoryService service = mock(HistoricalOhlcHistoryService.class);
    private final UUID marketId = UUID.randomUUID();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalHistoricalOhlcController(service)).build();
    }

    @Test
    void exposesBoundedAsOfRead() throws Exception {
        when(service.findStrictObserved(any(), any(), any(), anyInt())).thenReturn(
                new HistoricalOhlcSeries(marketId, OhlcInterval.FIFTEEN_MINUTES,
                        Instant.parse("2026-10-15T10:20:00Z"), List.of(),
                        HistoricalOhlcSeries.HistoricalOhlcSeriesStatus.INCOMPLETE, "missing"));

        mockMvc.perform(get("/internal/v1/historical-ohlc/" + marketId)
                        .param("interval", OhlcInterval.FIFTEEN_MINUTES.name())
                        .param("asOf", "2026-10-15T10:20:00Z")
                        .param("limit", "10"))
                .andExpect(status().isOk());

        verify(service).findStrictObserved(marketId, OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T10:20:00Z"), 10);
    }

    @Test
    void exposesCapturedRangeRead() throws Exception {
        when(service.findCapturedRange(any(), any(), any(), any(), any())).thenReturn(
                new HistoricalOhlcRange(marketId, OhlcInterval.FIFTEEN_MINUTES,
                        Instant.parse("2026-10-15T10:00:00Z"),
                        Instant.parse("2026-10-15T11:00:00Z"),
                        HistoricalOhlcEvidencePolicy.CAPTURED, List.of(),
                        HistoricalOhlcRangeStatus.UNAVAILABLE, "missing", 4, 0,
                        null, null, List.of(), "fingerprint", List.of()));

        mockMvc.perform(get("/internal/v1/historical-ohlc/" + marketId + "/range")
                        .param("interval", OhlcInterval.FIFTEEN_MINUTES.name())
                        .param("fromExclusive", "2026-10-15T10:00:00Z")
                        .param("toInclusive", "2026-10-15T11:00:00Z")
                        .param("evidencePolicy", "CAPTURED"))
                .andExpect(status().isOk());

        verify(service).findCapturedRange(marketId, OhlcInterval.FIFTEEN_MINUTES,
                Instant.parse("2026-10-15T10:00:00Z"),
                Instant.parse("2026-10-15T11:00:00Z"), HistoricalOhlcEvidencePolicy.CAPTURED);
    }

    @Test
    void rejectsUnsupportedEvidencePolicyBinding() throws Exception {
        mockMvc.perform(get("/internal/v1/historical-ohlc/" + marketId + "/range")
                        .param("interval", OhlcInterval.FIFTEEN_MINUTES.name())
                        .param("fromExclusive", "2026-10-15T10:00:00Z")
                        .param("toInclusive", "2026-10-15T11:00:00Z")
                        .param("evidencePolicy", "UNSUPPORTED"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
