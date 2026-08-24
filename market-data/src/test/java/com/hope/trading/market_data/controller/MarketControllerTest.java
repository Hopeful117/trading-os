package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.dto.MarketResponse;
import com.hope.trading.market_data.helper.MarketMapper;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamParameters;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.service.MarketHistoryService;
import com.hope.trading.market_data.service.MarketService;
import com.hope.trading.market_data.service.MarketSubscriptionService;
import com.hope.trading.market_data.service.MarketSynchronization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STORY-0020A-3C: the market catalogue is the entry point of every trader
 * journey — protects catalogue listing, instrument lookup, sync trigger,
 * subscription lifecycle and OHLC history contracts.
 */
class MarketControllerTest {

    private final MarketSynchronization synchronization = mock(MarketSynchronization.class);
    private final MarketService marketService = mock(MarketService.class);
    private final MarketSubscriptionService subscriptionService =
            mock(MarketSubscriptionService.class);
    private final MarketHistoryService historyService = mock(MarketHistoryService.class);

    private MockMvc mockMvc;

    private final UUID marketId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketController(
                        synchronization, marketService, new MarketMapper(),
                        subscriptionService, historyService))
                .build();
    }

    private Market market() {
        Market market = new Market();
        market.setMarketId(marketId);
        market.setProvider(MarketProvider.KRAKEN);
        market.setSymbol("XBT/EUR");
        market.setBaseAsset("XBT");
        market.setQuoteAsset("EUR");
        return market;
    }

    @Test
    void catalogueIsListed() throws Exception {
        when(marketService.findAll()).thenReturn(List.of(market()));

        mockMvc.perform(get("/api/v1/markets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("XBT/EUR"));
    }

    @Test
    void unknownMarketFailsExplicitly() throws Exception {
        when(marketService.findById(marketId)).thenReturn(Optional.empty());

        // Current contract: unknown market surfaces a raw RuntimeException
        // (no dedicated @ExceptionHandler exists for this controller).
        assertThatThrownBy(() -> mockMvc.perform(
                get("/api/v1/markets/" + marketId)))
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Market not found");
    }

    @Test
    void synchronizeTriggersCatalogueSync() throws Exception {
        mockMvc.perform(post("/api/v1/markets/synchronize"))
                .andExpect(status().isOk());

        verify(synchronization).synchronizeMarkets();
    }

    @Test
    void subscriptionLifecycleDelegatesToSubscriptionService() throws Exception {
        var request = new MarketStreamRequest(
                MarketStreamType.TICKER, new MarketStreamParameters(null, null));

        mockMvc.perform(post("/api/v1/markets/" + marketId + "/subscriptions")
                        .contentType("application/json")
                        .content("""
                            {"type":"TICKER","parameters":{"interval":null,"depth":null}}
                            """))
                .andExpect(status().isNoContent());
        verify(subscriptionService).subscribe(
                org.mockito.ArgumentMatchers.eq(marketId),
                org.mockito.ArgumentMatchers.any(MarketStreamRequest.class));

        mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.delete(
                                "/api/v1/markets/" + marketId + "/subscriptions")
                        .contentType("application/json")
                        .content("""
                            {"type":"TICKER","parameters":{"interval":null,"depth":null}}
                            """))
                .andExpect(status().isNoContent());
        verify(subscriptionService).unsubscribe(
                org.mockito.ArgumentMatchers.eq(marketId),
                org.mockito.ArgumentMatchers.any(MarketStreamRequest.class));
    }

    @Test
    void ohlcHistoryIsExposedWithIntervalAndLimit() throws Exception {
        var event = new OhlcEvent(
                marketId, com.hope.trading.market_data.helper.MarketProvider.KRAKEN,
                "XBT/EUR", OhlcInterval.FIFTEEN_MINUTES,
                Instant.now(), Instant.now().plusSeconds(900),
                new BigDecimal("100"), new BigDecimal("110"),
                new BigDecimal("95"), new BigDecimal("105"),
                new BigDecimal("12.5"), new BigDecimal("102"), 42,
                true, Instant.now());
        when(historyService.findOhlcHistory(marketId,
                OhlcInterval.FIFTEEN_MINUTES, 50)).thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/markets/" + marketId + "/ohlc")
                        .param("interval", "FIFTEEN_MINUTES")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].close").value(105));
    }
}
