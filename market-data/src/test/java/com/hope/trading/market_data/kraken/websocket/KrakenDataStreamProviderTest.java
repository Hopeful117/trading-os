package com.hope.trading.market_data.kraken.websocket;

import com.hope.trading.market_data.kraken.config.KrakenProperties;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamParameters;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
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

import java.net.URI;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-3: offline behavioral tests for the Kraken stream provider,
 * exercising the real message dispatch through a controlled WebSocket
 * session boundary. No network, no sleeps.
 */
class KrakenDataStreamProviderTest {

    /** Captures the session handler and replays a scripted session. */
    static class CapturingConnector implements KrakenStreamConnector {
        Function<WebSocketSession, Mono<Void>> handler;
        final List<String> sent = new CopyOnWriteArrayList<>();
        private Sinks.Many<String> inbound = Sinks.many().unicast().onBackpressureBuffer();
        private WebSocketSession session;

        void scriptSession(WebSocketSession session, Sinks.Many<String> inbound) {
            this.session = session;
            this.inbound = inbound;
        }

        @Override
        public Mono<Void> connect(
                URI uri, Function<WebSocketSession, Mono<Void>> sessionHandler) {
            this.handler = sessionHandler;
            return sessionHandler.apply(session);
        }
    }

    private CapturingConnector connector;
    private KrakenDataStreamProvider provider;
    private WebSocketSession session;
    private Sinks.Many<String> inbound;
    private final List<String> outboundTexts = new CopyOnWriteArrayList<>();

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

    private void establishConnection() {
        inbound = Sinks.many().unicast().onBackpressureBuffer();
        session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.textMessage(anyString())).thenAnswer(inv -> {
            var message = mock(
                    org.springframework.web.reactive.socket.WebSocketMessage.class);
            when(message.getPayloadAsText()).thenReturn(inv.getArgument(0));
            return message;
        });
        // Capture outbound payloads by subscribing to the flux handed to send().
        when(session.send(any())).thenAnswer(inv -> {
            Flux<WebSocketMessage> flux = inv.getArgument(0);
            flux.map(WebSocketMessage::getPayloadAsText).subscribe(outboundTexts::add);
            return Mono.empty();
        });
        when(session.receive()).thenReturn(inbound.asFlux()
                .map(text -> {
                    var message = mock(
                            org.springframework.web.reactive.socket.WebSocketMessage.class);
                    when(message.getPayloadAsText()).thenReturn(text);
                    return message;
                }));
        when(session.close()).thenReturn(Mono.empty());

        connector.scriptSession(session, inbound);

        // Trigger the connection lifecycle synchronously.
        provider.connect().block(java.time.Duration.ofSeconds(2));
    }

    private void subscribeTicker() {
        provider.subscribe(List.of(market), new MarketStreamRequest(
                MarketStreamType.TICKER,
                new MarketStreamParameters(null, null))).block();
    }

    @Test
    void connectEstablishesSessionThroughTheBoundary() {
        establishConnection();

        assertThat(connector.handler).isNotNull();
        assertThat(provider).isNotNull();
    }

    @Test
    void subscribeEmitsKrakenSubscribeCommandForEverySymbol() {
        establishConnection();

        subscribeTicker();

        assertThat(outboundTexts).anySatisfy(payload -> {
            assertThat(payload).contains("\"method\":\"subscribe\"");
            assertThat(payload).contains("ticker");
            assertThat(payload).contains("XBT/EUR");
        });
    }

    @Test
    void tickerMessageIsMappedAndDispatchedToSubscribedMarket() {
        establishConnection();
        subscribeTicker();

        var expectedEvent = new com.hope.trading.market_data.model.TickerEvent(
                market.getMarketId(), MarketProvider.KRAKEN, "BTC/EUR",
                new BigDecimal("50000"), new BigDecimal("50010"),
                new BigDecimal("50005"), new BigDecimal("12.5"),
                Instant.now());
        when(tickerMapper.toEvent(any(), any())).thenReturn(expectedEvent);

        inbound.tryEmitNext("""
            {"channel":"ticker","type":"snapshot","data":[
              {"symbol":"XBT/EUR","bid":50000,"ask":50010,"last":50005,
               "volume":12.5,"vwap":50002}
            ]}
            """);

        Mockito.verify(tickerPublisher)
                .publish(Mockito.argThat(event ->
                        "BTC/EUR".equals(event.symbol())
                                && event.bid().compareTo(new BigDecimal("50000")) == 0));
    }

    @Test
    void malformedMessageNeverProducesSilentMarketData() {
        establishConnection();
        subscribeTicker();

        inbound.tryEmitNext("{not valid json");

        Mockito.verifyNoInteractions(tickerPublisher, ohlcPublisher,
                orderBookPublisher, tradesPublisher);
    }

    @Test
    void unknownChannelAndHeartbeatAreIgnoredWithoutDispatch() {
        establishConnection();
        subscribeTicker();

        inbound.tryEmitNext("{\"channel\":\"heartbeat\"}");
        inbound.tryEmitNext("{\"channel\":\"unknown-channel\",\"data\":[]}");

        Mockito.verifyNoInteractions(tickerPublisher, ohlcPublisher,
                orderBookPublisher, tradesPublisher);
    }

    @Test
    void tickerForUnsubscribedMarketIsNotDispatched() {
        establishConnection();

        // No subscription registered for this market.
        inbound.tryEmitNext("""
            {"channel":"ticker","type":"snapshot","data":[
              {"symbol":"ETH/USD","bid":1,"ask":2,"last":3,"volume":4,"vwap":5}
            ]}
            """);

        Mockito.verify(tickerPublisher, never()).publish(any());
    }

    @Test
    void disconnectClosesTheUnderlyingSession() {
        establishConnection();

        provider.disconnect().block();

        verify(session).close();
    }

}
