package com.hope.trading.market_data.kraken.websocket;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.config.KrakenProperties;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamParameters;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.model.OrderBookSnapshot;
import com.hope.trading.market_data.service.OhlcEventPublisher;
import com.hope.trading.market_data.service.OrderBookEventPublisher;
import com.hope.trading.market_data.service.OrderBookStateService;
import com.hope.trading.market_data.service.RecentTradesEventPublisher;
import com.hope.trading.market_data.service.RecentTradesStateService;
import com.hope.trading.market_data.service.TickerEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-3B: completes the offline behavioral coverage of the Kraken
 * stream provider dispatch — OHLC, order book (snapshot/update), trades and
 * control messages — through the controlled WebSocket session boundary.
 */
class KrakenDataProviderDispatchTest {

    private CapturingConnector connector;
    private KrakenDataStreamProvider provider;
    private WebSocketSession session;
    private Sinks.Many<String> inbound;

    private final TickerEventPublisher tickerPublisher = mock(TickerEventPublisher.class);
    private final OhlcEventPublisher ohlcPublisher = mock(OhlcEventPublisher.class);
    private final OrderBookEventPublisher orderBookPublisher =
            mock(OrderBookEventPublisher.class);
    private final OrderBookStateService orderBookStateService =
            mock(OrderBookStateService.class);
    private final RecentTradesEventPublisher tradesPublisher =
            mock(RecentTradesEventPublisher.class);
    private final RecentTradesStateService recentTradesStateService =
            mock(RecentTradesStateService.class);
    private final com.hope.trading.market_data.kraken.helper.KrakenTickerMapper tickerMapper =
            mock(com.hope.trading.market_data.kraken.helper.KrakenTickerMapper.class);
    private final com.hope.trading.market_data.kraken.helper.KrakenOhlcMapper ohlcMapper =
            mock(com.hope.trading.market_data.kraken.helper.KrakenOhlcMapper.class);
    private final com.hope.trading.market_data.kraken.helper.KrakenOrderBookMapper
            orderBookMapper = mock(
            com.hope.trading.market_data.kraken.helper.KrakenOrderBookMapper.class);
    private final com.hope.trading.market_data.kraken.helper.KrakenTradeMapper tradeMapper =
            mock(com.hope.trading.market_data.kraken.helper.KrakenTradeMapper.class);

    private Market market;

    @BeforeEach
    void setUp() {
        connector = new CapturingConnector();
        var properties = new KrakenProperties();
        properties.setWebsocket("wss://ws.kraken.com/v2");

        provider = new KrakenDataStreamProvider(
                connector, properties, new ObjectMapper(),
                tickerMapper, tickerPublisher, ohlcPublisher, ohlcMapper,
                orderBookMapper, orderBookStateService, orderBookPublisher,
                tradeMapper, recentTradesStateService, tradesPublisher);

        market = new Market();
        market.setMarketId(UUID.randomUUID());
        market.setProvider(MarketProvider.KRAKEN);
        market.setSymbol("XBT/EUR");
    }

    private static class CapturingConnector implements KrakenStreamConnector {
        Function<WebSocketSession, Mono<Void>> handler;
        WebSocketSession session;
        Sinks.Many<String> inbound = Sinks.many().unicast().onBackpressureBuffer();

        @Override
        public Mono<Void> connect(
                URI uri, Function<WebSocketSession, Mono<Void>> sessionHandler) {
            this.handler = sessionHandler;
            return sessionHandler.apply(session);
        }
    }

    private void establishConnectionAndSubscribeTicker() {
        inbound = Sinks.many().unicast().onBackpressureBuffer();
        session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.textMessage(anyString())).thenAnswer(inv -> {
            var message = mock(WebSocketMessage.class);
            Mockito.when(message.getPayloadAsText()).thenReturn(inv.getArgument(0));
            return message;
        });
        when(session.send(any())).thenReturn(Mono.empty());
        when(session.receive())
                .thenReturn(inbound.asFlux()
                        .map(text -> {
                            var message = mock(WebSocketMessage.class);
                            Mockito.when(message.getPayloadAsText()).thenReturn(text);
                            return message;
                        }));
        when(session.close()).thenReturn(Mono.empty());

        connector.session = session;
        provider.connect().block(java.time.Duration.ofSeconds(2));
    }

    private void establishConnectionAndSubscribeOrderBook(int depth) {
        establishConnectionAndSubscribeTicker();
        provider.subscribe(List.of(market), new MarketStreamRequest(
                MarketStreamType.ORDER_BOOK, new MarketStreamParameters(null, depth)))
                .block();
    }

    private void subscribeTicker() {
        provider.subscribe(List.of(market), new MarketStreamRequest(
                MarketStreamType.TICKER, new MarketStreamParameters(null, null))).block();
    }

    private void emit(String json) {
        inbound.tryEmitNext(json);
    }

    // ---- OHLC --------------------------------------------------------------

    @Test
    void ohlcMessageIsMappedAndPublishedForSubscribedMarket() {
        establishConnectionAndSubscribeTicker();
        subscribeTicker();

        var event = mock(com.hope.trading.market_data.model.OhlcEvent.class);
        when(ohlcMapper.toEvent(any(), any(), any(Instant.class), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(event);

        emit("""
            {"channel":"ohlc","type":"snapshot",
             "timestamp":"2026-08-23T10:00:00Z",
             "data":[{"symbol":"XBT/EUR","open":"100","high":"110","low":"95",
                      "close":"105","trades":10,"volume":"12.5","vwap":"102",
                      "interval_begin":"2026-08-23T09:45:00Z","interval":15,"timestamp":"2026-08-23T10:00:00Z"}]}
            """);

        verify(ohlcPublisher).publish(event);
    }

    // ---- ORDER BOOK --------------------------------------------------------

    @Test
    void orderBookSnapshotIsInitializedThroughStateService() {
        establishConnectionAndSubscribeOrderBook(10);

        var snapshot = mock(OrderBookSnapshot.class);
        when(orderBookStateService.initialize(any())).thenReturn(snapshot);

        emit("""
            {"channel":"book","type":"snapshot","data":[{
               "symbol":"XBT/EUR",
               "bids":[{"price":"50000","qty":"1.5"},{"price":"49990","qty":"2.0"}],
               "asks":[{"price":"50010","qty":"0.8"}],
               "checksum":123,"timestamp":"2026-08-23T10:00:00Z"}]}
            """);

        verify(orderBookStateService).initialize(any());
        verify(orderBookPublisher).publish(snapshot);
    }

    @Test
    void orderBookUpdateWithoutSubscribedDepthIsIgnored() {
        establishConnectionAndSubscribeTicker();
        // Ticker subscription registered no order-book depth.

        emit("""
            {"channel":"book","type":"update","data":[{
               "symbol":"XBT/EUR",
               "bids":[["50000","1.5"]],
               "asks":[["50010","0.8"]],
               "checksum":123,"timestamp":"2026-08-23T10:00:00Z"}]}
            """);

        Mockito.verifyNoInteractions(orderBookStateService, orderBookPublisher);
    }

    // ---- TRADES ------------------------------------------------------------

    @Test
    void tradeMessagesAreBatchedPerMarketAndPublished() {
        establishConnectionAndSubscribeTicker();
        subscribeTicker();

        var snapshot = mock(com.hope.trading.market_data.model.RecentTradesSnapshot.class);
        when(recentTradesStateService.addBatch(any())).thenReturn(snapshot);

        emit("""
            {"channel":"trade","type":"snapshot","data":[
              {"symbol":"XBT/EUR","side":"buy","qty":"0.5","price":"50000",
               "ord_type":"market","trade_id":1,"timestamp":"2026-08-23T10:00:00Z"},
              {"symbol":"XBT/EUR","side":"sell","qty":"0.2","price":"49990",
               "ord_type":"market","trade_id":2,"timestamp":"2026-08-23T10:00:01Z"}
            ]}
            """);

        verify(recentTradesStateService).addBatch(any());
        verify(tradesPublisher).publish(snapshot);
    }

    // ---- SUBSCRIPTION LIFECYCLE --------------------------------------------

    @Test
    void unsubscribeRemovesRegistrationSoLaterDataIsIgnored() {
        establishConnectionAndSubscribeTicker();
        subscribeTicker();

        var snapshot = mock(com.hope.trading.market_data.model.RecentTradesSnapshot.class);
        when(recentTradesStateService.addBatch(any())).thenReturn(snapshot);

        emit("""
            {"channel":"trade","type":"update","data":[
              {"symbol":"XBT/EUR","side":"buy","qty":"0.5","price":"50000",
               "ord_type":"market","trade_id":1,"timestamp":"2026-08-23T10:00:00Z"}
            ]}
            """);
        verify(recentTradesStateService).addBatch(any());

        provider.unsubscribe(List.of(market), new MarketStreamRequest(
                MarketStreamType.TRADES, new MarketStreamParameters(null, null))).block();

        Mockito.clearInvocations(recentTradesStateService, tradesPublisher);

        emit("""
            {"channel":"trade","type":"update","data":[
              {"symbol":"XBT/EUR","side":"buy","qty":"0.5","price":"50000",
               "ord_type":"market","trade_id":2,"timestamp":"2026-08-23T10:01:00Z"}
            ]}
            """);

        Mockito.verifyNoInteractions(recentTradesStateService);
    }

    @Test
    void orderBookUnsubscribeReleasesDepthRegistration() {
        establishConnectionAndSubscribeOrderBook(10);

        provider.unsubscribe(List.of(market), new MarketStreamRequest(
                MarketStreamType.ORDER_BOOK, new MarketStreamParameters(null, 10))).block();

        Mockito.clearInvocations(orderBookStateService, orderBookPublisher);

        emit("""
            {"channel":"book","type":"snapshot","data":[{
               "symbol":"XBT/EUR",
               "bids":[{"price":"50000","qty":"1.5"}],
               "asks":[{"price":"50010","qty":"0.8"}],
               "checksum":123,"timestamp":"2026-08-23T10:05:00Z"}]}
            """);

        Mockito.verifyNoInteractions(orderBookStateService);
    }

    @Test
    void subscriptionRequestWithOnlyBlankSymbolsRegistersNothing() {
        establishConnectionAndSubscribeTicker();

        var blank = new Market();
        blank.setMarketId(UUID.randomUUID());
        blank.setProvider(MarketProvider.KRAKEN);
        blank.setSymbol("   ");

        provider.subscribe(List.of(blank), new MarketStreamRequest(
                MarketStreamType.TICKER, new MarketStreamParameters(null, null))).block();

        emit("""
            {"channel":"ticker","type":"snapshot","data":[
              {"symbol":"XBT/EUR","bid":49990,"ask":50010,"last":50000}
            ]}
            """);

        Mockito.verifyNoInteractions(tickerMapper, tickerPublisher);
    }

    @Test
    void nullMarketListYieldsEmptySubscription() {
        establishConnectionAndSubscribeTicker();
        provider.subscribe(null, new MarketStreamRequest(
                MarketStreamType.TICKER, new MarketStreamParameters(null, null))).block();

        emit("{\"method\":\"subscribe\",\"success\":true}");

        Mockito.verifyNoInteractions(tickerPublisher);
    }

    @Test
    void disconnectClosesOpenSessionAndClearsIt() {
        establishConnectionAndSubscribeTicker();
        provider.disconnect().block(java.time.Duration.ofSeconds(2));
        verify(session).close();
    }

    @Test
    void disconnectWithClosedSessionJustClearsReference() {
        establishConnectionAndSubscribeTicker();
        when(session.isOpen()).thenReturn(false);
        provider.disconnect().block(java.time.Duration.ofSeconds(2));
        verify(session, never()).close();
    }

    @Test
    void connectionTerminationClearsRealtimeStatesAndAllowsReconnect() {
        establishConnectionAndSubscribeOrderBook(10);
        provider.subscribe(List.of(market), new MarketStreamRequest(
                MarketStreamType.TRADES, new MarketStreamParameters(null, null))).block();

        inbound.tryEmitComplete();

        var key = new com.hope.trading.market_data.model.OrderBookKey(market.getMarketId(), 10);
        verify(orderBookStateService, org.mockito.Mockito.timeout(2000)).clear(key);
        verify(orderBookPublisher, org.mockito.Mockito.timeout(2000)).clear(key);
        verify(recentTradesStateService, org.mockito.Mockito.timeout(2000))
                .clear(market.getMarketId());
        verify(tradesPublisher, org.mockito.Mockito.timeout(2000)).clear(market.getMarketId());

        // After termination the provider must be able to reconnect cleanly.
        establishConnectionAndSubscribeTicker();
        assertThat(provider.connect().block(java.time.Duration.ofSeconds(2))).isNull();
    }

    // ---- UNKNOWN / INVALID DATA --------------------------------------------

    @Test
    void ohlcDataWithoutSubscribedMarketIsIgnored() {
        establishConnectionAndSubscribeTicker();
        subscribeTicker();

        emit("""
            {"channel":"ohlc","type":"snapshot",
             "timestamp":"2026-08-23T10:00:00Z",
             "data":[{"symbol":"ETH/USD","open":"100","high":"110","low":"95",
                      "close":"105","trades":10,"volume":"12.5","vwap":"102",
                      "interval_begin":"2026-08-23T09:45:00Z","interval":15,"timestamp":"2026-08-23T10:00:00Z"}]}
            """);

        Mockito.verifyNoInteractions(ohlcMapper, ohlcPublisher);
    }

    @Test
    void emptyDataArraysAreIgnoredForEveryChannel() {
        establishConnectionAndSubscribeTicker();
        subscribeTicker();

        emit("{\"channel\":\"ticker\",\"type\":\"snapshot\",\"data\":[]}");
        emit("{\"channel\":\"ohlc\",\"type\":\"snapshot\",\"data\":[]}");
        emit("{\"channel\":\"book\",\"type\":\"snapshot\",\"data\":[]}");
        emit("{\"channel\":\"trade\",\"type\":\"snapshot\",\"data\":[]}");

        Mockito.verifyNoInteractions(tickerPublisher, ohlcPublisher,
                orderBookPublisher, tradesPublisher);
    }

    @Test
    void invalidTradeEntryDoesNotBreakRemainingTradesInBatch() {
        establishConnectionAndSubscribeTicker();
        subscribeTicker();

        var snapshot = mock(com.hope.trading.market_data.model.RecentTradesSnapshot.class);
        when(recentTradesStateService.addBatch(any())).thenReturn(snapshot);
        when(tradeMapper.toEvent(any(), any()))
                .thenThrow(new IllegalArgumentException("bad side"))
                .thenReturn(mock(com.hope.trading.market_data.model.TradeEvent.class));

        emit("""
            {"channel":"trade","type":"update","data":[
              {"symbol":"XBT/EUR","side":"weird","qty":"0.5","price":"50000",
               "ord_type":"market","trade_id":1,"timestamp":"2026-08-23T10:00:00Z"},
              {"symbol":"XBT/EUR","side":"buy","qty":"0.2","price":"49990",
               "ord_type":"market","trade_id":2,"timestamp":"2026-08-23T10:00:01Z"}
            ]}
            """);

        verify(recentTradesStateService).addBatch(any());
        verify(tradesPublisher).publish(snapshot);
    }

    @Test
    void unknownChannelIsIgnoredWithoutError() {
        establishConnectionAndSubscribeTicker();

        emit("{\"channel\":\"something_new\",\"type\":\"snapshot\",\"data\":[{}]}");

        Mockito.verifyNoInteractions(tickerPublisher, ohlcPublisher,
                orderBookPublisher, tradesPublisher);
    }

    // ---- CONTROL -----------------------------------------------------------

    @Test
    void controlSuccessAndFailureAreAcceptedWithoutDispatch() {
        establishConnectionAndSubscribeTicker();

        emit("{\"method\":\"subscribe\",\"success\":true}");
        emit("{\"method\":\"subscribe\",\"success\":false,"
                + "\"error\":\"invalid channel\"}");

        Mockito.verifyNoInteractions(tickerPublisher, ohlcPublisher,
                orderBookPublisher, tradesPublisher);
    }
}
