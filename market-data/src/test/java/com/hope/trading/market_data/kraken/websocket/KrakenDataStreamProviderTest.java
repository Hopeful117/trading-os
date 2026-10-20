package com.hope.trading.market_data.kraken.websocket;

import com.hope.trading.market_data.kraken.config.KrakenProperties;
import com.hope.trading.market_data.kraken.dto.KrakenMessageType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.config.OrderBookEvidenceCaptureProperties;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamParameters;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.model.OrderBookDelta;
import com.hope.trading.market_data.service.OhlcEventPublisher;
import com.hope.trading.market_data.service.OrderBookEventPublisher;
import com.hope.trading.market_data.service.OrderBookEvidenceCaptureService;
import com.hope.trading.market_data.service.OrderBookStateService;
import com.hope.trading.market_data.service.RecentTradesEventPublisher;
import com.hope.trading.market_data.service.RecentTradesStateService;
import com.hope.trading.market_data.service.TickerEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.CloseStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import org.mockito.InOrder;
import org.reactivestreams.Subscription;
import reactor.core.publisher.SignalType;
import reactor.core.Disposable;

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
        private WebSocketSession session;
        private KrakenTransportTerminationEvidence terminationEvidence = KrakenTransportTerminationEvidence.empty();
        private boolean evidenceCleared;

        void scriptSession(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public Mono<Void> connect(
                UUID connectionId, URI uri, Function<WebSocketSession, Mono<Void>> sessionHandler) {
            this.handler = sessionHandler;
            return sessionHandler.apply(session);
        }

        @Override
        public KrakenTransportTerminationEvidence terminationEvidence(UUID connectionId) {
            return terminationEvidence;
        }

        @Override
        public void clearTerminationEvidence(UUID connectionId) {
            evidenceCleared = true;
        }
    }

    private CapturingConnector connector;
    private KrakenDataStreamProvider provider;
    private WebSocketSession session;
    private Sinks.Many<String> inbound;
    private final AtomicReference<Subscription> receiveSubscription = new AtomicReference<>();
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
    private final OrderBookEvidenceCaptureService evidenceCaptureService =
            mock(OrderBookEvidenceCaptureService.class);
    private final KrakenStreamLifecycleDiagnostics lifecycleDiagnostics =
            mock(KrakenStreamLifecycleDiagnostics.class);
    private final KrakenStreamTerminationRepository terminationRepository =
            mock(KrakenStreamTerminationRepository.class);
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
        properties.setFuturesWebsocket("wss://futures.kraken.com/ws/v1");

        OrderBookEvidenceCaptureProperties captureProperties = new OrderBookEvidenceCaptureProperties();
        captureProperties.setEnabled(true);
        provider = new KrakenDataStreamProvider(
                connector, properties, new ObjectMapper(),
                tickerMapper, tickerPublisher, ohlcPublisher, ohlcMapper,
                orderBookMapper, orderBookStateService, orderBookPublisher,
                evidenceCaptureService, captureProperties, lifecycleDiagnostics,
                tradeMapper, recentTradesStateService, tradesPublisher);
        provider.setTerminationRepository(terminationRepository);

        market = new Market();
        market.setMarketId(UUID.randomUUID());
        market.setProvider(MarketProvider.KRAKEN);
        market.setSymbol("XBT/EUR");
    }

    private void establishConnection() {
        prepareSession();

        // Trigger the connection lifecycle synchronously.
        provider.connect().block(java.time.Duration.ofSeconds(2));
    }

    private void prepareSession() {
        inbound = Sinks.many().multicast().onBackpressureBuffer();
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
                .doOnSubscribe(receiveSubscription::set)
                .map(text -> {
                    var message = mock(
                            org.springframework.web.reactive.socket.WebSocketMessage.class);
                    when(message.getPayloadAsText()).thenReturn(text);
                    return message;
                }));
        when(session.close()).thenReturn(Mono.empty());
        when(session.closeStatus()).thenReturn(Mono.empty());

        connector.scriptSession(session);
    }

    private void subscribeTicker() {
        provider.subscribe(List.of(market), new MarketStreamRequest(
                MarketStreamType.TICKER,
                new MarketStreamParameters(null, null))).block();
    }

    private void subscribeTrades() {
        provider.subscribe(List.of(market), new MarketStreamRequest(
                MarketStreamType.TRADES,
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
    void firstSubscriptionSendsCommandAfterOpeningConnection() {
        prepareSession();

        subscribeTicker();

        assertThat(outboundTexts).anySatisfy(payload -> {
            assertThat(payload).contains("\"method\":\"subscribe\"");
            assertThat(payload).contains("ticker");
            assertThat(payload).contains("XBT/EUR");
        });
    }

    @Test
    void futuresSubscriptionIsIncludedInLifecycleDiagnostics() {
        prepareSession();
        Market futuresMarket = new Market();
        futuresMarket.setMarketId(UUID.randomUUID());
        futuresMarket.setProvider(MarketProvider.KRAKEN);
        futuresMarket.setProductType(InstrumentProductType.LINEAR_PERPETUAL);
        futuresMarket.setProviderSymbol("PF_XBTUSD");

        provider.subscribe(List.of(futuresMarket), new MarketStreamRequest(
                MarketStreamType.ORDER_BOOK,
                new MarketStreamParameters(null, 25))).block();

        verify(lifecycleDiagnostics, atLeastOnce()).subscriptionRequested(any(), eq("FUTURES"),
                eq("book"), eq(List.of("PF_XBTUSD")), eq(25));
        assertThat(outboundTexts).anySatisfy(payload -> assertThat(payload)
                .contains("\"feed\":\"book\"")
                .contains("PF_XBTUSD"));
    }

    @Test
    void normalCompletionIsDiagnosedBeforeRealtimeCleanup() {
        establishConnection();
        subscribeTrades();

        inbound.tryEmitComplete();

        InOrder order = inOrder(lifecycleDiagnostics, recentTradesStateService);
         order.verify(lifecycleDiagnostics).terminating(Mockito.argThat(context ->
                 context.product().equals("SPOT")
                         && context.signalType() == SignalType.ON_COMPLETE
                         && context.classification()
                         == KrakenStreamTerminationClassification.UNKNOWN_TRANSPORT_COMPLETION));
        order.verify(recentTradesStateService).clear(market.getMarketId());
    }

    @Test
    void streamErrorIsDiagnosedBeforeRealtimeCleanup() {
        establishConnection();
        subscribeTrades();
        IllegalStateException failure = new IllegalStateException("stream failed");

        inbound.tryEmitError(failure);

        InOrder order = inOrder(lifecycleDiagnostics, recentTradesStateService);
         order.verify(lifecycleDiagnostics).terminating(Mockito.argThat(context ->
                 context.product().equals("SPOT")
                         && context.signalType() == SignalType.ON_ERROR
                         && context.classification()
                         == KrakenStreamTerminationClassification.NETWORK_FAILURE
                         && context.exceptionClass().equals(failure.getClass().getName())));
        order.verify(recentTradesStateService).clear(market.getMarketId());
    }

    @Test
    void diagnosticPersistenceFailureDoesNotBlockRealtimeCleanup() {
        establishConnection();
        subscribeTrades();
        doThrow(new IllegalStateException("diagnostics unavailable"))
                .when(terminationRepository).append(any());

        inbound.tryEmitComplete();

        verify(recentTradesStateService, timeout(1000)).clear(market.getMarketId());
        verify(lifecycleDiagnostics, timeout(1000)).cleanupCompleted(any(), eq("SPOT"));
    }

    @Test
    void slowDiagnosticPersistenceDoesNotBlockRealtimeCleanup() throws Exception {
        CountDownLatch persistenceStarted = new CountDownLatch(1);
        CountDownLatch releasePersistence = new CountDownLatch(1);
        doAnswer(invocation -> {
            persistenceStarted.countDown();
            releasePersistence.await(2, TimeUnit.SECONDS);
            return true;
        }).when(terminationRepository).append(any());

        establishConnection();
        subscribeTrades();
        inbound.tryEmitComplete();

        assertThat(persistenceStarted.await(1, TimeUnit.SECONDS)).isTrue();
        verify(lifecycleDiagnostics, timeout(1000)).cleanupCompleted(any(), eq("SPOT"));
        releasePersistence.countDown();
    }

    @Test
    void staleConnectionCallbackCannotInstallAReplacementSession() throws Exception {
        establishConnection();
        Function<WebSocketSession, Mono<Void>> staleHandler = connector.handler;
        Map<Object, UUID> connectionIds = connectionIds();
        Object product = connectionIds.keySet().iterator().next();
        UUID oldConnectionId = connectionIds.get(product);
        connectionIds.put(product, UUID.randomUUID());

        WebSocketSession replacement = mock(WebSocketSession.class);
        assertThat(staleHandler.apply(replacement).block()).isNull();
        assertThat(sessionReference().get()).isSameAs(session);

        connectionIds.put(product, oldConnectionId);
        provider.disconnect().block();
        inbound.tryEmitComplete();
    }

    @Test
    void staleDisconnectCleanupCannotClearAConnectionOwnedSession() throws Exception {
        establishConnection();
        Map<Object, UUID> connectionIds = connectionIds();
        Object product = connectionIds.keySet().iterator().next();
        UUID oldConnectionId = connectionIds.get(product);
        connectionIds.put(product, UUID.randomUUID());

        Method clearSessionIfOwner = KrakenDataStreamProvider.class.getDeclaredMethod(
                "clearSessionIfOwner", product.getClass(), UUID.class);
        clearSessionIfOwner.setAccessible(true);
        clearSessionIfOwner.invoke(provider, product, oldConnectionId);

        assertThat(sessionReference().get()).isSameAs(session);

        connectionIds.put(product, oldConnectionId);
        provider.disconnect().block();
        inbound.tryEmitComplete();
    }

    @Test
    void closeStatusIsPersistedAsRemoteCloseWhenAvailable() {
        prepareSession();
        when(session.closeStatus()).thenReturn(Mono.just(new CloseStatus(1001, "going away")));
        provider.connect().block(java.time.Duration.ofSeconds(2));
        subscribeTicker();
        connector.terminationEvidence = new KrakenTransportTerminationEvidence(
                "channel-1", true, 1001, "going away", true, Instant.now(),
                false, false, null, null, false, null, null);

        inbound.tryEmitComplete();

        verify(lifecycleDiagnostics).terminating(Mockito.argThat(context ->
                context.classification() == KrakenStreamTerminationClassification.REMOTE_CLOSE
                        && context.closeCode() == 1001
                        && context.closeReason().equals("going away")));
        verify(terminationRepository, timeout(1000)).append(Mockito.argThat(context ->
                context.classification() == KrakenStreamTerminationClassification.REMOTE_CLOSE
                        && context.closeCode() == 1001));
    }

    @Test
    void closeFrameEvidenceWinsOverTransportError() {
        prepareSession();
        provider.connect().block(java.time.Duration.ofSeconds(2));
        subscribeTicker();
        connector.terminationEvidence = new KrakenTransportTerminationEvidence(
                "channel-1", true, 1001, "going away", true, Instant.now(),
                false, false, null, null, true, "java.io.IOException", "java.io.IOException");

        inbound.tryEmitError(new IllegalStateException("stream failed"));

        verify(lifecycleDiagnostics).terminating(Mockito.argThat(context ->
                context.classification() == KrakenStreamTerminationClassification.REMOTE_CLOSE));
    }

    @Test
    void terminationConsumesCorrelatedTransportEvidenceExactlyOnce() {
        establishConnection();
        subscribeTrades();
        connector.terminationEvidence = new KrakenTransportTerminationEvidence(
                "channel-1", true, 1001, "going away", true, Instant.now(),
                false, false, null, null, false, null, null);

        inbound.tryEmitComplete();

        verify(terminationRepository, timeout(1000)).append(Mockito.argThat(context ->
                context.transportEvidence().nettyChannelId().equals("channel-1")
                        && context.transportEvidence().websocketCloseObserved()
                        && context.classification() == KrakenStreamTerminationClassification.REMOTE_CLOSE));
        assertThat(connector.evidenceCleared).isTrue();
    }

    @Test
    void streamCancellationIsDiagnosedBeforeRealtimeCleanup() throws Exception {
        establishConnection();
        subscribeTrades();

        cancelProviderConnection();

        InOrder order = inOrder(lifecycleDiagnostics, recentTradesStateService);
         order.verify(lifecycleDiagnostics).terminating(Mockito.argThat(context ->
                 context.product().equals("SPOT")
                         && context.signalType() == SignalType.CANCEL
                         && context.classification()
                         == KrakenStreamTerminationClassification.LOCAL_DISPOSE));
        order.verify(recentTradesStateService).clear(market.getMarketId());
    }

    @SuppressWarnings("unchecked")
    private void cancelProviderConnection() throws Exception {
        Field field = KrakenDataStreamProvider.class.getDeclaredField("connectionSubscription");
        field.setAccessible(true);
        AtomicReference<Disposable> subscription = (AtomicReference<Disposable>) field.get(provider);
        subscription.get().dispose();
    }

    @SuppressWarnings("unchecked")
    private Map<Object, UUID> connectionIds() throws Exception {
        Field field = KrakenDataStreamProvider.class.getDeclaredField("connectionIds");
        field.setAccessible(true);
        return (Map<Object, UUID>) field.get(provider);
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<WebSocketSession> sessionReference() throws Exception {
        Field field = KrakenDataStreamProvider.class.getDeclaredField("session");
        field.setAccessible(true);
        return (AtomicReference<WebSocketSession>) field.get(provider);
    }

    @Test
    void duplicateUnsubscribeOnlyEmitsRemoteCommandForLastConsumer() {
        establishConnection();
        subscribeTicker();
        subscribeTicker();

        provider.unsubscribe(List.of(market), new MarketStreamRequest(
                MarketStreamType.TICKER,
                new MarketStreamParameters(null, null))).block();

        assertThat(outboundTexts.stream()
                .filter(payload -> payload.contains("\"method\":\"unsubscribe\"")))
                .isEmpty();

        provider.unsubscribe(List.of(market), new MarketStreamRequest(
                MarketStreamType.TICKER,
                new MarketStreamParameters(null, null))).block();

        assertThat(outboundTexts.stream()
                .filter(payload -> payload.contains("\"method\":\"unsubscribe\"")))
                .hasSize(1);
    }

    @Test
    void reconnectReplaysActiveSubscriptions() {
        establishConnection();
        subscribeTicker();

        long subscribeCommandsBeforeReconnect = outboundTexts.stream()
                .filter(payload -> payload.contains("\"method\":\"subscribe\""))
                .count();

        inbound.tryEmitComplete();
        establishConnection();

        assertThat(outboundTexts.stream()
                .filter(payload -> payload.contains("\"method\":\"subscribe\"")))
                .hasSizeGreaterThan((int) subscribeCommandsBeforeReconnect);
    }

    @Test
    void futuresTerminationSchedulesBoundedRecoveryForActiveSubscriptions() {
        prepareSession();
        Market futuresMarket = new Market();
        futuresMarket.setMarketId(UUID.randomUUID());
        futuresMarket.setProvider(MarketProvider.KRAKEN);
        futuresMarket.setProductType(InstrumentProductType.LINEAR_PERPETUAL);
        futuresMarket.setProviderSymbol("PF_XBTUSD");

        provider.subscribe(List.of(futuresMarket), new MarketStreamRequest(
                MarketStreamType.ORDER_BOOK,
                new MarketStreamParameters(null, 25))).block();

        inbound.tryEmitComplete();

        verify(lifecycleDiagnostics, timeout(1500)).recoveryScheduled(
                eq("FUTURES"), eq(1), eq(java.time.Duration.ofSeconds(1)));
    }

    @Test
    void recoveryIsNotSuccessfulBeforeFreshSnapshotAcceptance() {
        prepareSession();
        Market futuresMarket = new Market();
        futuresMarket.setMarketId(UUID.randomUUID());
        futuresMarket.setProvider(MarketProvider.KRAKEN);
        futuresMarket.setProductType(InstrumentProductType.LINEAR_PERPETUAL);
        futuresMarket.setProviderSymbol("PF_XBTUSD");

        provider.subscribe(List.of(futuresMarket), new MarketStreamRequest(
                MarketStreamType.ORDER_BOOK,
                new MarketStreamParameters(null, 25))).block();
        inbound.tryEmitComplete();

        verify(lifecycleDiagnostics, timeout(1500)).recoveryStarted(eq("FUTURES"), eq(1));
        verify(lifecycleDiagnostics, after(200).never()).recoverySucceeded(eq("FUTURES"), eq(1));
        provider.disconnect().block();
    }

    @Test
    void recoverySucceedsOnlyAfterFreshSnapshotAcceptance() {
        prepareSession();
        Market futuresMarket = futuresMarket();
        OrderBookDelta snapshot = new OrderBookDelta(
                futuresMarket.getMarketId(), MarketProvider.KRAKEN, "BTC/USD", "PF_XBTUSD", 25,
                com.hope.trading.market_data.model.OrderBookDeltaType.SNAPSHOT,
                List.of(), List.of(), Instant.parse("2026-10-18T12:00:00Z"), null, 100L);
        when(evidenceCaptureService.acceptSnapshot(snapshot)).thenReturn(true);
        when(orderBookMapper.toDelta(any(), eq(KrakenMessageType.SNAPSHOT), eq(futuresMarket), eq(25)))
                .thenReturn(snapshot);

        provider.subscribe(List.of(futuresMarket), new MarketStreamRequest(
                MarketStreamType.ORDER_BOOK, new MarketStreamParameters(null, 25))).block();
        inbound.tryEmitComplete();
        prepareSession();

        verify(lifecycleDiagnostics, timeout(1500)).recoveryStarted(eq("FUTURES"), eq(1));
        inbound.tryEmitNext("""
                {"feed":"book_snapshot","product_id":"PF_XBTUSD","bids":[],"asks":[],
                 "timestamp":"2026-10-18T12:00:00Z","seq":100}
                """);

        verify(evidenceCaptureService, timeout(1000)).acceptSnapshot(snapshot);
        verify(lifecycleDiagnostics, timeout(1000)).recoverySucceeded(eq("FUTURES"), eq(1));
        provider.disconnect().block();
    }

    @Test
    void pendingRecoveryRechecksSubscriptionsBeforeReconnect() {
        prepareSession();
        Market futuresMarket = futuresMarket();

        provider.subscribe(List.of(futuresMarket), new MarketStreamRequest(
                MarketStreamType.ORDER_BOOK, new MarketStreamParameters(null, 25))).block();
        inbound.tryEmitComplete();
        verify(lifecycleDiagnostics, timeout(500)).recoveryScheduled(
                eq("FUTURES"), eq(1), eq(java.time.Duration.ofSeconds(1)));

        provider.unsubscribe(List.of(futuresMarket), new MarketStreamRequest(
                MarketStreamType.ORDER_BOOK, new MarketStreamParameters(null, 25))).block();

        verify(lifecycleDiagnostics, after(1300).times(1)).connecting(any(), eq("FUTURES"));
    }

    private Market futuresMarket() {
        Market futuresMarket = new Market();
        futuresMarket.setMarketId(UUID.randomUUID());
        futuresMarket.setProvider(MarketProvider.KRAKEN);
        futuresMarket.setProductType(InstrumentProductType.LINEAR_PERPETUAL);
        futuresMarket.setProviderSymbol("PF_XBTUSD");
        return futuresMarket;
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

        verify(tickerPublisher).publish(Mockito.argThat(event ->
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

        verify(tickerPublisher, never()).publish(any());
    }

    @Test
    void disconnectClosesTheUnderlyingSession() {
        establishConnection();

        provider.disconnect().block();
        inbound.tryEmitComplete();

        verify(session).close();
        verify(lifecycleDiagnostics).terminating(Mockito.argThat(context ->
                context.classification() == KrakenStreamTerminationClassification.APPLICATION_SHUTDOWN));
    }

}
