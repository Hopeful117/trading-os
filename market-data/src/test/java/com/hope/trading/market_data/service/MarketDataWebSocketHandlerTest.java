package com.hope.trading.market_data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_data.helper.MarketProvider;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.getField;

/**
 * STORY-0020A-3B: protects the frontend-facing WebSocket contract of Market
 * Data — query-parameter routing to the matching publisher stream, JSON
 * event forwarding, and deterministic subscription disposal on disconnect.
 */
class MarketDataWebSocketHandlerTest {

    private final TickerEventPublisher tickerPublisher = mock(TickerEventPublisher.class);
    private final OhlcEventPublisher ohlcPublisher = mock(OhlcEventPublisher.class);
    private final OrderBookEventPublisher orderBookPublisher =
            mock(OrderBookEventPublisher.class);
    private final RecentTradesEventPublisher tradesPublisher =
            mock(RecentTradesEventPublisher.class);

    private MarketDataWebSocketHandler handler;
    private WebSocketSession session;
    private final List<String> sentPayloads = new ArrayList<>();

    private void givenSession() throws Exception {
        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        when(session.isOpen()).thenReturn(true);
        org.mockito.Mockito.doAnswer(inv -> {
            sentPayloads.add(((TextMessage) inv.getArgument(0)).getPayload());
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
        handler = new MarketDataWebSocketHandler(
                tickerPublisher, ohlcPublisher, orderBookPublisher,
                tradesPublisher, new ObjectMapper().findAndRegisterModules());
    }

    private void connect(String pathAndQuery) {
        when(session.getUri()).thenReturn(URI.create("ws://localhost" + pathAndQuery));
    }

    @Test
    void tickerSubscriptionForwardsPublishedEventsAsJson() throws Exception {
        givenSession();
        connect("/ws/market-data?symbol=BTC%2FEUR&type=TICKER");

        Sinks.Many<com.hope.trading.market_data.model.TickerEvent> sink =
                Sinks.many().unicast().onBackpressureBuffer();
        when(tickerPublisher.streamBySymbol("BTC/EUR")).thenReturn(sink.asFlux());

        handler.afterConnectionEstablished(session);

        sink.tryEmitNext(new com.hope.trading.market_data.model.TickerEvent(
                UUID.randomUUID(), MarketProvider.KRAKEN, "BTC/EUR",
                new BigDecimal("50000"), new BigDecimal("50010"),
                new BigDecimal("50005"), BigDecimal.TEN, Instant.now()));

        assertThat(sentPayloads).anySatisfy(payload ->
                assertThat(payload).contains("BTC/EUR").contains("50005"));
    }

    @Test
    void ohlcSubscriptionForwardsEventsForMarketAndInterval() throws Exception {
        givenSession();
        UUID marketId = UUID.randomUUID();
        connect("/ws/market-data?symbol=BTC%2FEUR&marketId=" + marketId
                + "&interval=15&type=OHLC");

        var interval = com.hope.trading.market_data.model.OhlcInterval.FIFTEEN_MINUTES;
        var event = new com.hope.trading.market_data.model.OhlcEvent(
                marketId, MarketProvider.KRAKEN, "BTC/EUR", interval,
                Instant.now(), Instant.now().plusSeconds(900),
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("5"), BigDecimal.ONE, BigDecimal.ONE,
                3, false, Instant.now());
        when(ohlcPublisher.streamByMarketAndInterval(marketId, interval))
                .thenReturn(Flux.just(event));

        handler.afterConnectionEstablished(session);

        assertThat(sentPayloads).anySatisfy(payload ->
                assertThat(payload).contains("BTC/EUR").contains("close"));
    }

    @Test
    void tradeSubscriptionIsRoutedByMarketId() throws Exception {
        givenSession();
        UUID marketId = UUID.randomUUID();
        AtomicReference<UUID> requested = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(inv -> {
            requested.set(inv.getArgument(0));
            return Flux.never();
        }).when(tradesPublisher).streamByMarket(any(UUID.class));

        connect("/ws/market-data?symbol=BTC%2FEUR&marketId=" + marketId
                + "&type=TRADES");
        handler.afterConnectionEstablished(session);

        assertThat(requested.get()).isEqualTo(marketId);
    }

    @Test
    void disconnectDisposesTheClientSubscription() throws Exception {
        givenSession();
        connect("/ws/market-data?symbol=BTC%2FEUR&type=TICKER");

        AtomicBoolean cancelled = new AtomicBoolean(false);
        when(tickerPublisher.streamBySymbol("BTC/EUR"))
                .thenReturn(Flux.<com.hope.trading.market_data.model.TickerEvent>never()
                .doOnCancel(() -> cancelled.set(true)));

        handler.afterConnectionEstablished(session);
        assertThat(cancelled.get()).isFalse();

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(cancelled.get())
                .as("client disconnect must dispose the upstream subscription")
                .isTrue();
    }

    @Test
    void unknownStreamTypeIsRejectedExplicitly() throws Exception {
        givenSession();
        connect("/ws/market-data?symbol=BTC%2FEUR&type=NOT_A_STREAM");

        assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsupportedOrderBookDepthIsRejectedBeforeSubscription() throws Exception {
        givenSession();
        UUID marketId = UUID.randomUUID();
        connect("/ws/market-data?symbol=BTC%2FEUR&marketId=" + marketId
                + "&depth=100&type=ORDER_BOOK");

        assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
                .hasMessage("Unsupported order-book depth: 100");
        verify(orderBookPublisher, never()).streamByMarketAndDepth(any(), anyInt());
    }

    @Test
    void unsupportedOhlcIntervalIsRejectedBeforeSubscription() throws Exception {
        givenSession();
        UUID marketId = UUID.randomUUID();
        connect("/ws/market-data?symbol=BTC%2FEUR&marketId=" + marketId
                + "&interval=2&type=OHLC");

        assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
                .hasMessage("Unsupported OHLC interval: 2");
        verify(ohlcPublisher, never()).streamByMarketAndInterval(any(), any());
    }

    @Test
    void duplicateRequiredParameterIsRejected() throws Exception {
        givenSession();
        connect("/ws/market-data?symbol=BTC%2FEUR&symbol=ETH%2FEUR&type=TICKER");

        assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
                .hasMessage("WebSocket parameter must appear exactly once: symbol");
        verify(tickerPublisher, never()).streamBySymbol(any());
    }

    @Test
    void oversizedSymbolIsRejectedBeforeSubscription() throws Exception {
        givenSession();
        connect("/ws/market-data?symbol=" + "A".repeat(65) + "&type=TICKER");

        assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
                .hasMessage("Invalid WebSocket symbol");
        verify(tickerPublisher, never()).streamBySymbol(any());
    }

    @Test
    void malformedIntegerIsRejectedBeforeSubscription() throws Exception {
        givenSession();
        UUID marketId = UUID.randomUUID();
        connect("/ws/market-data?symbol=BTC%2FEUR&marketId=" + marketId
                + "&depth=invalid&type=ORDER_BOOK");

        assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
                .hasMessage("Invalid WebSocket integer parameter: depth");
        verify(orderBookPublisher, never()).streamByMarketAndDepth(any(), anyInt());
    }

    @Test
    void invalidMarketIdIsRejectedBeforeSubscription() throws Exception {
        givenSession();
        connect("/ws/market-data?symbol=BTC%2FEUR&marketId=invalid"
                + "&depth=10&type=ORDER_BOOK");

        assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderBookPublisher, never()).streamByMarketAndDepth(any(), anyInt());
    }

    @Test
    void controlCharacterSymbolIsRejectedBeforeSubscription() throws Exception {
        givenSession();
        connect("/ws/market-data?symbol=BTC%01EUR&type=TICKER");

        assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
                .hasMessage("Invalid WebSocket symbol");
        verify(tickerPublisher, never()).streamBySymbol(any());
    }

    @Test
    void publisherErrorClosesSessionAndRemovesFailedSubscription() throws Exception {
        givenSession();
        connect("/ws/market-data?symbol=BTC%2FEUR&type=TICKER");
        when(tickerPublisher.streamBySymbol("BTC/EUR"))
                .thenReturn(Flux.error(new IllegalStateException("publisher failed")));

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.SERVER_ERROR);
        assertThat((Map<String, ?>) getField(handler, "subscriptions"))
                .doesNotContainKey(session.getId());
    }
}
