package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.model.MarketFactsResponse;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.service.MarketFactsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalMarketFactsControllerTest {
    private final MarketFactsService service = mock(MarketFactsService.class);
    private final UUID marketId = UUID.randomUUID();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new InternalMarketFactsController(service)).build();
    }

    @Test
    void exposesExplicitBoundedFactRequest() throws Exception {
        when(service.find(any())).thenReturn(new MarketFactsResponse(
                marketId, "XBT/EUR", "XBT", "EUR", Instant.parse("2026-10-03T12:00:00Z"),
                null, null));

        mockMvc.perform(get("/internal/v1/market-facts/" + marketId)
                        .param("interval", OhlcInterval.ONE_MINUTE.name())
                        .param("activityWindowMinutes", "60")
                        .param("readinessLookbackCandles", "60")
                        .param("minimumCompletedCandles", "30")
                        .param("maxObservationAgeSeconds", "300"))
                .andExpect(status().isOk());

        verify(service).find(any());
    }
}
