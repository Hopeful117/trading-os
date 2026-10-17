package com.hope.trading.market_data.kraken.websocket;

import com.hope.trading.market_data.brokerclient.MarketDataStreamProvider;
import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.kraken.config.KrakenProperties;
import com.hope.trading.market_data.kraken.dto.*;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcMessage;
import com.hope.trading.market_data.kraken.dto.orderbook.KrakenOrderBookMessage;
import com.hope.trading.market_data.kraken.dto.orderbook.KrakenOrderBookData;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenTickerData;
import com.hope.trading.market_data.kraken.dto.subscription.KrakenSubscriptionMethod;
import com.hope.trading.market_data.kraken.dto.subscription.KrakenSubscriptionParams;
import com.hope.trading.market_data.kraken.dto.subscription.KrakenSubscriptionRequest;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenTickerMessage;
import com.hope.trading.market_data.kraken.dto.trade.KrakenTradeMessage;
import com.hope.trading.market_data.kraken.helper.KrakenOhlcMapper;
import com.hope.trading.market_data.kraken.helper.KrakenOrderBookMapper;
import com.hope.trading.market_data.kraken.helper.KrakenTickerMapper;
import com.hope.trading.market_data.kraken.helper.KrakenTradeMapper;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OrderBookDelta;
import com.hope.trading.market_data.model.OrderBookKey;
import com.hope.trading.market_data.model.OrderBookSnapshot;
import com.hope.trading.market_data.model.RecentTradesSnapshot;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.model.TradeEvent;
import com.hope.trading.market_data.service.OhlcEventPublisher;
import com.hope.trading.market_data.service.OrderBookEventPublisher;
import com.hope.trading.market_data.service.OrderBookEvidenceCaptureService;
import com.hope.trading.market_data.config.OrderBookEvidenceCaptureProperties;
import com.hope.trading.market_data.service.OrderBookStateService;
import com.hope.trading.market_data.service.RecentTradesEventPublisher;
import com.hope.trading.market_data.service.RecentTradesStateService;
import com.hope.trading.market_data.service.TickerEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@Slf4j
public class KrakenDataStreamProvider implements MarketDataStreamProvider {
    private final KrakenStreamConnector connector;
    private final KrakenProperties krakenProperties;
    private final ObjectMapper objectMapper;
    private final KrakenTickerMapper tickerMapper;
    private final ConcurrentMap<String, Market> subscribedMarkets =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> marketSubscriptionCounts =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> streamSubscriptionCounts =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ActiveSubscription> activeSubscriptions =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<Integer>> orderBookDepthsBySymbol =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<Integer, AtomicInteger>>
            orderBookDepthSubscriptionCounts = new ConcurrentHashMap<>();
    private final Set<String> tradeSymbols =
            ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, AtomicInteger> tradeSubscriptionCounts =
            new ConcurrentHashMap<>();
    private final AtomicReference<WebSocketSession> session = new AtomicReference<>();
    private final AtomicReference<Disposable> connectionSubscription = new AtomicReference<>();
    private final AtomicReference<WebSocketSession> futuresSession = new AtomicReference<>();
    private final AtomicReference<Disposable> futuresConnectionSubscription = new AtomicReference<>();
    private final ConcurrentMap<Product, UUID> connectionIds = new ConcurrentHashMap<>();
    private final Sinks.Many<String> outboundMessages =
            Sinks.many()
                    .multicast()
                    .onBackpressureBuffer(
                            OUTBOUND_BUFFER_SIZE,
                            false
                    );
    private final Sinks.Many<String> futuresOutboundMessages =
            Sinks.many().multicast().onBackpressureBuffer(OUTBOUND_BUFFER_SIZE, false);
    private static final int OUTBOUND_BUFFER_SIZE = 256;
    private static final String TICKER_FEED = "ticker";
    private static final String BOOK_SNAPSHOT_FEED = "book_snapshot";
    private static final String TRADE_FEED = "trade";
    private static final String QTY_FIELD = "qty";
    private static final String PRODUCT_ID_FIELD = "product_id";
    private static final String PRICE_FIELD = "price";

    private final TickerEventPublisher tickerEventPublisher;
    private final OhlcEventPublisher ohlcEventPublisher;
    private final KrakenOhlcMapper ohlcMapper;
    private final KrakenOrderBookMapper orderBookMapper;
    private final OrderBookStateService orderBookStateService;
    private final OrderBookEventPublisher orderBookEventPublisher;
    private final OrderBookEvidenceCaptureService orderBookEvidenceCaptureService;
    private final OrderBookEvidenceCaptureProperties orderBookEvidenceCaptureProperties;
    private final KrakenStreamLifecycleDiagnostics lifecycleDiagnostics;
    private final KrakenTradeMapper tradeMapper;
    private final RecentTradesStateService recentTradesStateService;
    private final RecentTradesEventPublisher recentTradesEventPublisher;
    private final Object outboundEmissionLock =
            new Object();

    @Autowired
    public KrakenDataStreamProvider(
            KrakenStreamConnector connector,
            KrakenProperties krakenProperties,
            ObjectMapper objectMapper,
            KrakenTickerMapper tickerMapper,
            TickerEventPublisher tickerEventPublisher,
            OhlcEventPublisher ohlcEventPublisher,
            KrakenOhlcMapper ohlcMapper,
            KrakenOrderBookMapper orderBookMapper,
            OrderBookStateService orderBookStateService,
            OrderBookEventPublisher orderBookEventPublisher,
            OrderBookEvidenceCaptureService orderBookEvidenceCaptureService,
            OrderBookEvidenceCaptureProperties orderBookEvidenceCaptureProperties,
            KrakenStreamLifecycleDiagnostics lifecycleDiagnostics,
            KrakenTradeMapper tradeMapper,
            RecentTradesStateService recentTradesStateService,
            RecentTradesEventPublisher recentTradesEventPublisher
    ) {
        this.objectMapper = objectMapper;
        this.tickerMapper = tickerMapper;
        this.tickerEventPublisher = tickerEventPublisher;
        this.ohlcEventPublisher = ohlcEventPublisher;
        this.ohlcMapper = ohlcMapper;
        this.orderBookMapper = orderBookMapper;
        this.orderBookStateService = orderBookStateService;
        this.orderBookEventPublisher = orderBookEventPublisher;
        this.orderBookEvidenceCaptureService = orderBookEvidenceCaptureService;
        this.orderBookEvidenceCaptureProperties = orderBookEvidenceCaptureProperties;
        this.lifecycleDiagnostics = lifecycleDiagnostics;
        this.tradeMapper = tradeMapper;
        this.recentTradesStateService = recentTradesStateService;
        this.recentTradesEventPublisher = recentTradesEventPublisher;
        this.connector = connector;
        this.krakenProperties = krakenProperties;
    }

    public KrakenDataStreamProvider(
            KrakenStreamConnector connector,
            KrakenProperties krakenProperties,
            ObjectMapper objectMapper,
            KrakenTickerMapper tickerMapper,
            TickerEventPublisher tickerEventPublisher,
            OhlcEventPublisher ohlcEventPublisher,
            KrakenOhlcMapper ohlcMapper,
            KrakenOrderBookMapper orderBookMapper,
            OrderBookStateService orderBookStateService,
            OrderBookEventPublisher orderBookEventPublisher,
            KrakenTradeMapper tradeMapper,
            RecentTradesStateService recentTradesStateService,
            RecentTradesEventPublisher recentTradesEventPublisher
    ) {
        this(connector, krakenProperties, objectMapper, tickerMapper, tickerEventPublisher,
                ohlcEventPublisher, ohlcMapper, orderBookMapper, orderBookStateService,
                orderBookEventPublisher, null, new OrderBookEvidenceCaptureProperties(),
                new KrakenStreamLifecycleDiagnostics(), tradeMapper,
                recentTradesStateService, recentTradesEventPublisher);
    }


    @Override
    public synchronized Mono<Void> connect() {
        return connect(Product.SPOT);
    }

    private synchronized Mono<Void> connect(Product product) {
        if (session(product) != null && session(product).isOpen()) return Mono.empty();
        if (subscription(product) == null || subscription(product).isDisposed()) {
            UUID connectionId = UUID.randomUUID();
            connectionIds.put(product, connectionId);
            lifecycleDiagnostics.connecting(connectionId, product.name());
            AtomicReference<Throwable> terminationError = new AtomicReference<>();
            setSubscription(product, connector.connect(URI.create(url(product)), ws -> {
                setSession(product, ws);
                lifecycleDiagnostics.connected(connectionId, product.name());
                Mono<Void> outbound = ws.send(outbound(product).asFlux().map(ws::textMessage));
                Mono<Void> inbound = ws.receive().map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(message -> handleMessage(message, product)).then();
                return Mono.when(outbound, inbound);
            }).doOnError(error -> {
                        terminationError.set(error);
                        log.error("[KRAKEN] {} WebSocket connection failed connectionId={}", product,
                                connectionId, error);
                    })
                    .doFinally(signal -> {
                        lifecycleDiagnostics.terminating(connectionId, product.name(), signal,
                                terminationError.get(), activeSequenceDiagnostics(product));
                        clearRealtimeStates(product);
                        setSession(product, null);
                        setSubscription(product, null);
                        connectionIds.remove(product, connectionId);
                        lifecycleDiagnostics.cleanupCompleted(connectionId, product.name());
                    }).subscribe());
        }
        return waitUntilConnected(product)
                .then(replayActiveSubscriptions(product));
    }

    @Override
    public Mono<Void> disconnect() {
            WebSocketSession currentSession = this.session.get();
            Mono<Void> closeSpot = currentSession == null || !currentSession.isOpen()
                    ? Mono.empty()
                    : currentSession.close()
                    .doOnSuccess(ignored ->
                            log.info("Disconnected from Kraken websocket")
                    )
                    .doOnError(error ->
                            log.error(
                                    "Unable to disconnect from Kraken websocket",
                                    error
                            )
                    )
                    .doFinally(signal -> this.session.set(null));
            WebSocketSession currentFuturesSession = this.futuresSession.get();
            Mono<Void> closeFutures = currentFuturesSession == null || !currentFuturesSession.isOpen()
                    ? Mono.empty()
                     : currentFuturesSession.close().doFinally(signal -> this.futuresSession.set(null));
            return Mono.when(closeSpot, closeFutures).then();
        }




    @Override
    public Mono<Void> subscribe(
            List<Market> markets,
            MarketStreamRequest request
    ) {
        if (request.type() == MarketStreamType.OHLC && markets != null && markets.stream()
                .filter(Objects::nonNull).anyMatch(this::isFutures)) {
            return Mono.error(new UnsupportedOperationException(
                    "Kraken Futures OHLC WebSocket feed is unsupported"));
        }
        validateProviderSymbols(markets);
        registerMarkets(markets, request);
        return sendByProduct(markets, request, KrakenSubscriptionMethod.SUBSCRIBE)
                .doOnError(error ->
                        unregisterMarkets(markets, request)
                );
    }
    @Override
    public Mono<Void> unsubscribe(
            List<Market> markets,
            MarketStreamRequest request
    ) {
        validateProviderSymbols(markets);
        List<Market> remoteUnsubscribeMarkets = markets == null
                ? List.of()
                : markets.stream()
                .filter(Objects::nonNull)
                .filter(market -> streamSubscriptionCount(market, request) == 1)
                .toList();
        return sendByProduct(remoteUnsubscribeMarkets, request, KrakenSubscriptionMethod.UNSUBSCRIBE)
                .doOnSuccess(ignored ->
                unregisterMarkets(markets, request)
        );
    }
    private Mono<Void> sendByProduct(List<Market> markets, MarketStreamRequest request,
                                     KrakenSubscriptionMethod method) {
        if (markets == null) return Mono.empty();
        return Flux.fromIterable(markets.stream().filter(Objects::nonNull)
                        .collect(Collectors.groupingBy(this::product)).entrySet())
                .flatMap(entry -> {
                    Product product = entry.getKey();
                    List<Market> family = entry.getValue();
                    List<String> symbols = extractSymbols(family, product);
                    if (symbols.isEmpty()) return Mono.empty();
                    boolean alreadyConnected = session(product) != null && session(product).isOpen();
                    Mono<Void> connection = alreadyConnected ? Mono.empty() : connect(product);
                    return connection.then(alreadyConnected
                            ? sendSubscriptionRequest(method, resolveChannel(request),
                            symbols, request, product)
                            : Mono.empty());
                }).then();
    }

    private Mono<Void> replayActiveSubscriptions(Product product) {
        return Flux.fromIterable(activeSubscriptions.values())
                .filter(subscription -> product(subscription.market()) == product)
                .flatMap(subscription -> sendSubscriptionRequest(
                        KrakenSubscriptionMethod.SUBSCRIBE,
                        resolveChannel(subscription.request()),
                        extractSymbols(List.of(subscription.market()), product),
                        subscription.request(),
                        product))
                .then();
    }
    private void handleMessage(String message, Product product) {
        try {
            var root = objectMapper.readTree(message);

            if (product == Product.FUTURES && root.has("feed")) {
                switch (root.path("feed").asText().toLowerCase(Locale.ROOT)) {
                    case TICKER_FEED -> handleFuturesTicker(root);
                    case "book", BOOK_SNAPSHOT_FEED -> handleFuturesBook(root);
                    case TRADE_FEED -> handleFuturesTrades(root);
                    default -> log.debug("Ignoring Kraken Futures feed={}", root.path("feed").asText());
                }
                return;
            }

        if (!root.has("channel")) {
                handleControlMessage(root, product);
                return;
            }

            String channel = root.get("channel").asString()
                    .trim()
                    .toLowerCase();

            switch (channel) {
                case TICKER_FEED -> handleTickerMessage(root, product);
                case "ohlc" -> handleOhlcMessage(root, product);
                case "book", BOOK_SNAPSHOT_FEED -> handleOrderBookMessage(root, product);
                case TRADE_FEED -> handleTradeMessage(root, product);

                case "heartbeat" -> log.trace("Received Kraken heartbeat");
                default -> log.debug("Ignoring Kraken channel={}", channel);
            }

        } catch (Exception exception) {
            log.error(
                    "Error parsing Kraken websocket message: {}",
                    message,
                    exception
            );
        }
    }


    private List<String> extractSymbols(List<Market> markets, Product product) {

        if (markets == null) {
            return List.of();
        }

        return markets.stream()
                .filter(Objects::nonNull)
                .filter(market -> product == product(market))
                .map(market -> product == Product.FUTURES ? market.getProviderSymbol() : market.getSymbol())
                .filter(symbol ->
                        symbol != null && !symbol.isBlank()
                )
                .distinct()
                .toList();
    }
    private Mono<Void> sendSubscriptionRequest(
            KrakenSubscriptionMethod method,
            KrakenChannel channel,
            List<String> symbols,
            MarketStreamRequest streamRequest,
            Product product
    ) {
        final String payload;

        try {
            if (product == Product.FUTURES) {
                String futuresPayload = objectMapper.writeValueAsString(Map.of(
                        "event", method == KrakenSubscriptionMethod.SUBSCRIBE ? "subscribe" : "unsubscribe",
                        "feed", futuresFeed(streamRequest.type()), "product_ids", symbols));
                if (method == KrakenSubscriptionMethod.SUBSCRIBE) {
                    lifecycleDiagnostics.subscriptionRequested(connectionIds.get(product), product.name(),
                            futuresFeed(streamRequest.type()), symbols,
                            streamRequest.parameters() == null ? null : streamRequest.parameters().depth());
                }
                return emitOutboundMessage(futuresPayload, product);
            }
            KrakenSubscriptionParams parameters =
                    buildSubscriptionParams(
                            channel,
                            symbols,
                            streamRequest,
                            method == KrakenSubscriptionMethod.SUBSCRIBE
                    );

            KrakenSubscriptionRequest request =
                    new KrakenSubscriptionRequest(
                            method.getValue(),
                            parameters
                    );

            payload =
                    objectMapper.writeValueAsString(request);

        } catch (Exception exception) {
            return Mono.error(
                    new IllegalStateException(
                            "Unable to serialize Kraken subscription request",
                            exception
                    )
            );
        }

        if (method == KrakenSubscriptionMethod.SUBSCRIBE) {
            lifecycleDiagnostics.subscriptionRequested(connectionIds.get(product), product.name(),
                    resolveChannel(streamRequest).getValue(), symbols,
                    streamRequest.parameters() == null ? null : streamRequest.parameters().depth());
        }

        return emitOutboundMessage(payload, product);
    }

    private Mono<Void> waitUntilConnected(Product product) {

        return Mono.defer(() -> {

                    WebSocketSession currentSession = session(product);

                    if (currentSession != null
                            && currentSession.isOpen()) {
                        return Mono.just(Boolean.TRUE);
                    }

                    return Mono.empty();
                })
                .repeatWhenEmpty(repeat ->
                        repeat.delayElements(
                                Duration.ofMillis(50)
                        )
                )
                .timeout(
                        Duration.ofSeconds(10),
                        Mono.error(
                                new IllegalStateException(
                                        "Unable to establish Kraken websocket connection"
                                )
                        )
                )
                .then();
    }



    private String normalize(String symbol) {
        return symbol
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private KrakenChannel resolveChannel(
            MarketStreamRequest request
    ) {
        return switch (request.type()) {
            case TICKER -> KrakenChannel.TICKER;
            case OHLC -> KrakenChannel.OHLC;
            case TRADES -> KrakenChannel.TRADES;
            case ORDER_BOOK -> KrakenChannel.ORDER_BOOK;
        };
    }

    private KrakenSubscriptionParams buildSubscriptionParams(
            KrakenChannel channel,
            List<String> symbols,
            MarketStreamRequest request,
            boolean subscribe
    ) {
        return switch (request.type()) {
            case TICKER -> new KrakenSubscriptionParams(
                    channel.getValue(),
                    symbols,
                    null,
                    null,
                    "bbo",
                    subscribe ? true : null
            );

            case OHLC -> new KrakenSubscriptionParams(
                    channel.getValue(),
                    symbols,
                    request.parameters().interval(),
                    null,
                    null,
                    subscribe ? true : null
            );
            case ORDER_BOOK -> new KrakenSubscriptionParams(
                    channel.getValue(),
                    symbols,
                    null,
                    request.parameters().depth(),
                    null,
                    subscribe ? true : null
            );
            case TRADES -> new KrakenSubscriptionParams(
                    channel.getValue(),
                    symbols,
                    null,
                    null,
                    null,
                    subscribe ? true : null
            );
        };
    }
    private void handleTickerMessage(JsonNode root, Product product) {
        if (!root.has("data")
                || !root.get("data").isArray()
                || root.get("data").isEmpty()) {
            return;
        }

        if (product == Product.FUTURES) {
            root.get("data").forEach(this::handleFuturesTicker);
            return;
        }
        KrakenTickerMessage tickerMessage =
                objectMapper.treeToValue(
                        root,
                        KrakenTickerMessage.class
                );

        tickerMessage.getData()
                .forEach(data -> {
                    Market market = findSubscribedMarket(
                            data.getSymbol(), product
                    );

                    if (market == null) {
                        return;
                    }

                    TickerEvent event =
                            tickerMapper.toEvent(
                                    data,
                                    market
                            );

                    tickerEventPublisher.publish(event);
                });
    }

    private Market findSubscribedMarket(String symbol, Product product) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        return subscribedMarkets.get(subscriptionKey(symbol, product));
    }

    private void handleOhlcMessage(JsonNode root, Product product) {
        if (product == Product.FUTURES) return;

        if (!root.has("data")
                || !root.get("data").isArray()
                || root.get("data").isEmpty()) {
            return;
        }

        KrakenOhlcMessage ohlcMessage =
                objectMapper.treeToValue(
                        root,
                        KrakenOhlcMessage.class
                );

        ohlcMessage.data()
                .forEach(entry -> {
                    Market market = findSubscribedMarket(
                            entry.symbol(), product
                    );

                    if (market == null) {
                        log.warn(
                                "Ignoring Kraken OHLC data without subscribed market symbol={}",
                                entry.symbol()
                        );
                        return;
                    }

                    boolean closed =
                            entry.timestamp()
                                    .isBefore(ohlcMessage.timestamp());

                    OhlcEvent event =
                            ohlcMapper.toEvent(
                                    entry,
                                    market,
                                    ohlcMessage.timestamp(),
                                    closed
                            );

                    ohlcEventPublisher.publish(event);
                });
    }

    private void handleOrderBookMessage(JsonNode root, Product product) {
        if (!root.has("data")
                || !root.get("data").isArray()
                || root.get("data").isEmpty()) {
            return;
        }

        if (product == Product.FUTURES) {
            handleFuturesBook(root);
            return;
        }
        KrakenOrderBookMessage message =
                objectMapper.treeToValue(
                        root,
                        KrakenOrderBookMessage.class
                );

        message.data().forEach(data -> handleSpotOrderBookData(data, message, product));
    }

    private void handleSpotOrderBookData(KrakenOrderBookData data, KrakenOrderBookMessage message,
                                         Product product) {
        Market market = findSubscribedMarket(data.symbol(), product);
        if (market == null) {
            log.warn("Ignoring Kraken order-book data without subscribed market symbol={}", data.symbol());
            return;
        }
        Set<Integer> depths = orderBookDepthsBySymbol.get(subscriptionKey(data.symbol(), product));
        if (depths == null || depths.isEmpty()) {
            log.warn("Ignoring Kraken order-book data without active depth symbol={}", data.symbol());
            return;
        }
        List.copyOf(depths).forEach(depth -> handleOrderBookDelta(
                orderBookMapper.toDelta(data, message.type(), market, depth), message.type(), product, market, depth));
    }

    private void handleOrderBookDelta(OrderBookDelta delta, KrakenMessageType messageType,
                                      Product product, Market market, int depth) {
        boolean capture = shouldCaptureEvidence(product, market, depth);
        if (messageType == KrakenMessageType.SNAPSHOT) {
            if (!capture || orderBookEvidenceCaptureService.acceptSnapshot(delta)) {
                if (capture) {
                    lifecycleDiagnostics.snapshotAccepted(connectionIds.get(product),
                            orderBookEvidenceCaptureService.diagnosticSnapshot(
                                    new OrderBookKey(market.getMarketId(), depth)));
                }
                orderBookEventPublisher.publish(orderBookStateService.initialize(delta));
            }
            return;
        }
        if (capture && !orderBookEvidenceCaptureService.acceptUpdate(delta)) {
            clearOrderBook(market, depth, true);
            return;
        }
        orderBookStateService.update(delta).ifPresent(snapshot -> {
            if (capture) {
                orderBookEvidenceCaptureService.checkpoint(delta, snapshot);
            }
            orderBookEventPublisher.publish(snapshot);
        });
    }

    private void clearOrderBook(Market market, int depth, boolean invalidateEvidence) {
        OrderBookKey key = new OrderBookKey(market.getMarketId(), depth);
        orderBookStateService.clear(key);
        orderBookEventPublisher.clear(key);
        if (invalidateEvidence) {
            orderBookEvidenceCaptureService.invalidate(key);
        }
    }

    private void handleTradeMessage(JsonNode root, Product product) {
        if (!root.has("data")
                || !root.get("data").isArray()
                || root.get("data").isEmpty()) {
            return;
        }

        if (product == Product.FUTURES) {
            handleFuturesTrades(root);
            return;
        }
        KrakenTradeMessage message =
                objectMapper.treeToValue(
                        root,
                        KrakenTradeMessage.class
                );
        Map<UUID, List<TradeEvent>> tradesByMarket =
                new LinkedHashMap<>();

        message.data().forEach(data -> {
            Market market = findSubscribedMarket(data.symbol(), product);

            if (market == null) {
                log.warn(
                        "Ignoring Kraken trade without subscribed market symbol={}",
                        data.symbol()
                );
                return;
            }

            try {
                TradeEvent event = tradeMapper.toEvent(data, market);
                tradesByMarket.computeIfAbsent(
                        market.getMarketId(),
                        ignored -> new ArrayList<>()
                ).add(event);
            } catch (IllegalArgumentException exception) {
                log.warn(
                        "Ignoring invalid Kraken trade symbol={} tradeId={} reason={}",
                        data.symbol(),
                        data.tradeId(),
                        exception.getMessage()
                );
            }
        });

        tradesByMarket.values().forEach(trades -> {
            RecentTradesSnapshot snapshot =
                    recentTradesStateService.addBatch(trades);
            recentTradesEventPublisher.publish(snapshot);
        });
    }

    private void registerMarkets(
            List<Market> markets,
            MarketStreamRequest request
    ) {
        if (markets == null) return;
        markets.stream()
                .filter(Objects::nonNull)
                .forEach(market -> {
                    Product product = product(market);
                    String symbol = subscriptionKey(market, product);
                    String streamKey = streamSubscriptionKey(market, request);
                    subscribedMarkets.put(symbol, market);
                    streamSubscriptionCounts.computeIfAbsent(
                            streamKey, ignored -> new AtomicInteger()).incrementAndGet();
                    activeSubscriptions.put(streamKey, new ActiveSubscription(market, request));
                    marketSubscriptionCounts.computeIfAbsent(
                                 symbol,
                            ignored -> new AtomicInteger()
                    ).incrementAndGet();

                    if (request.type()
                            == MarketStreamType.ORDER_BOOK) {
                        orderBookDepthsBySymbol.computeIfAbsent(
                                symbol,
                                ignored -> ConcurrentHashMap.newKeySet()
                        ).add(request.parameters().depth());
                        orderBookDepthSubscriptionCounts
                                .computeIfAbsent(symbol, ignored -> new ConcurrentHashMap<>())
                                .computeIfAbsent(request.parameters().depth(), ignored -> new AtomicInteger())
                                .incrementAndGet();
                    }
                    if (request.type() == MarketStreamType.TRADES) {
                        tradeSymbols.add(symbol);
                        tradeSubscriptionCounts
                                .computeIfAbsent(symbol, ignored -> new AtomicInteger())
                                .incrementAndGet();
                    }
                });
    }

    private void unregisterMarkets(
            List<Market> markets,
            MarketStreamRequest request
    ) {
        if (markets == null) return;
        markets.stream().filter(Objects::nonNull)
                .forEach(market -> unregisterMarket(market, request));
    }

    private void unregisterMarket(Market market, MarketStreamRequest request) {
        Product product = product(market);
        String symbol = subscriptionKey(market, product);
        String streamKey = streamSubscriptionKey(market, request);
        decrement(streamSubscriptionCounts, streamKey, activeSubscriptions);
        if (request.type() == MarketStreamType.ORDER_BOOK) {
            unregisterOrderBookDepth(symbol, request.parameters().depth());
        }
        if (request.type() == MarketStreamType.TRADES) {
            decrementTradeSubscription(symbol);
        }
        marketSubscriptionCounts.computeIfPresent(symbol, (ignored, count) -> {
            if (count.decrementAndGet() <= 0) {
                subscribedMarkets.remove(symbol, market);
                return null;
            }
            return count;
        });
    }

    private void decrement(ConcurrentMap<String, AtomicInteger> counts, String key,
                           ConcurrentMap<String, ActiveSubscription> subscriptions) {
        AtomicInteger count = counts.get(key);
        if (count != null && count.decrementAndGet() <= 0) {
            counts.remove(key, count);
            subscriptions.remove(key);
        }
    }

    private void unregisterOrderBookDepth(String symbol, Integer depth) {
        Set<Integer> depths = orderBookDepthsBySymbol.get(symbol);
        ConcurrentMap<Integer, AtomicInteger> counts = orderBookDepthSubscriptionCounts.get(symbol);
        if (depths == null || counts == null) {
            return;
        }
        AtomicInteger count = counts.get(depth);
        if (count != null && count.decrementAndGet() <= 0) {
            counts.remove(depth, count);
            depths.remove(depth);
        }
        if (depths.isEmpty()) {
            orderBookDepthsBySymbol.remove(symbol, depths);
        }
        if (counts.isEmpty()) {
            orderBookDepthSubscriptionCounts.remove(symbol, counts);
        }
    }

    private void decrementTradeSubscription(String symbol) {
        AtomicInteger count = tradeSubscriptionCounts.get(symbol);
        if (count != null && count.decrementAndGet() <= 0) {
            tradeSubscriptionCounts.remove(symbol, count);
            tradeSymbols.remove(symbol);
        }
    }

    private void clearRealtimeStates(Product product) {
        orderBookDepthsBySymbol.forEach((symbol, depths) -> {
            if (!symbol.startsWith(product.name() + ":")) return;
            Market market = subscribedMarkets.get(symbol);

            if (market == null) {
                return;
            }

            depths.forEach(depth -> {
                OrderBookKey key =
                        new OrderBookKey(market.getMarketId(), depth);
                orderBookStateService.clear(key);
                orderBookEventPublisher.clear(key);
                if (orderBookEvidenceCaptureService != null) {
                    orderBookEvidenceCaptureService.invalidate(key);
                }
            });
        });
        tradeSymbols.forEach(symbol -> {
            if (!symbol.startsWith(product.name() + ":")) return;
            Market market = subscribedMarkets.get(symbol);

            if (market == null) {
                return;
            }

            recentTradesStateService.clear(market.getMarketId());
            recentTradesEventPublisher.clear(market.getMarketId());
        });
    }

    private List<OrderBookEvidenceCaptureService.SequenceDiagnostics> activeSequenceDiagnostics(Product product) {
        List<OrderBookEvidenceCaptureService.SequenceDiagnostics> result = new ArrayList<>();
        orderBookDepthsBySymbol.forEach((symbol, depths) -> {
            if (!symbol.startsWith(product.name() + ":")) return;
            Market market = subscribedMarkets.get(symbol);
            if (market == null || orderBookEvidenceCaptureService == null) return;
            depths.forEach(depth -> {
                OrderBookEvidenceCaptureService.SequenceDiagnostics diagnostics =
                        orderBookEvidenceCaptureService.diagnosticSnapshot(
                                new OrderBookKey(market.getMarketId(), depth));
                if (diagnostics != null) result.add(diagnostics);
            });
        });
        return List.copyOf(result);
    }
    private void handleControlMessage(JsonNode root, Product product) {
        if (root.has("success")) {
            boolean success = root.get("success").asBoolean();

            if (success) {
                log.info(
                        "Kraken control message succeeded method={}",
                        root.path("method").asString()
                );
                lifecycleDiagnostics.subscriptionConfirmed(connectionIds.get(product), product.name(),
                        root.path("method").asString());
            } else {
                log.warn(
                        "Kraken control message failed method={} error={}",
                        root.path("method").asString(),
                        root.path("error").asString()
                );
            }
        }
    }
    private Mono<Void> emitOutboundMessage(String payload, Product product) {
        Sinks.EmitResult result;

        synchronized (outboundEmissionLock) {
            result = outbound(product).tryEmitNext(payload);
        }

        if (result.isFailure()) {
            return Mono.error(
                    new IllegalStateException(
                            "Unable to queue Kraken subscription request: "
                                    + result
                    )
            );
        }

        return Mono.empty();
    }

    private void handleFuturesTicker(JsonNode data) {
         String symbol = data.path(PRODUCT_ID_FIELD).asText(null);
        Market market = findSubscribedMarket(symbol, Product.FUTURES);
        if (market == null) return;
        com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesTickerDto ticker =
                new com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesTickerDto();
        ticker.setSymbol(symbol);
        ticker.setBid(decimal(data, "bid"));
        ticker.setAsk(decimal(data, "ask"));
        ticker.setLast(decimal(data, "last"));
        ticker.setLastTime(timestamp(data, "time"));
        if (ticker.getBid() == null || ticker.getAsk() == null || ticker.getLast() == null
                || ticker.getLastTime() == null) return;
        KrakenTickerData generic = new KrakenTickerData();
        generic.setSymbol(symbol);
        generic.setBid(ticker.getBid());
        generic.setAsk(ticker.getAsk());
        generic.setLast(ticker.getLast());
         tickerEventPublisher.publish(tickerMapper.toEvent(generic, market, ticker.getLastTime()));
    }

    private void handleFuturesBook(JsonNode root) {
        JsonNode values = root.has("data") && root.get("data").isArray()
                ? root.get("data") : objectMapper.createArrayNode().add(root);
        KrakenMessageType type = BOOK_SNAPSHOT_FEED.equalsIgnoreCase(root.path("feed").asText())
                ? KrakenMessageType.SNAPSHOT : KrakenMessageType.UPDATE;
        values.forEach(data -> handleFuturesBookData(data, type));
    }

    private void handleFuturesBookData(JsonNode data, KrakenMessageType type) {
        String symbol = data.path(PRODUCT_ID_FIELD).asText(null);
        Market market = findSubscribedMarket(symbol, Product.FUTURES);
        if (market == null) return;
        Set<Integer> depths = orderBookDepthsBySymbol.get(subscriptionKey(symbol, Product.FUTURES));
        if (depths == null) return;
        var book = new KrakenOrderBookData(symbol, levels(data.get("bids")), levels(data.get("asks")),
                data.has("checksum") ? data.get("checksum").asLong() : null,
                timestamp(data, "timestamp"), data.has("seq") ? data.get("seq").asLong() : null);
        depths.forEach(depth -> handleOrderBookDelta(
                orderBookMapper.toDelta(book, type, market, depth), type, Product.FUTURES, market, depth));
    }

    private boolean shouldCaptureEvidence(Product product, Market market, int depth) {
        return orderBookEvidenceCaptureService != null
                && orderBookEvidenceCaptureProperties.isEnabled()
                && product == Product.FUTURES
                && depth == orderBookEvidenceCaptureProperties.getDepth()
                && orderBookEvidenceCaptureProperties.getProviderSymbol().equals(market.getProviderSymbol());
    }

    private void handleFuturesTrades(JsonNode root) {
        Map<UUID, List<TradeEvent>> byMarket = new LinkedHashMap<>();
        JsonNode values = root.has("data") && root.get("data").isArray()
                ? root.get("data") : objectMapper.createArrayNode().add(root);
        values.forEach(data -> {
            String symbol = data.path(PRODUCT_ID_FIELD).asText(null);
            Market market = findSubscribedMarket(symbol, Product.FUTURES);
            if (market == null) return;
            try {
                String tradeId = data.has("trade_id")
                        ? data.get("trade_id").asText()
                        : data.path("uid").asText(null);
                var trade = new com.hope.trading.market_data.kraken.dto.trade.KrakenTradeData(
                        symbol, data.path("side").asText(null), decimal(data, QTY_FIELD),
                         decimal(data, PRICE_FIELD), data.path("ord_type").asText(null),
                        tradeId, timestamp(data, "time"));
                byMarket.computeIfAbsent(market.getMarketId(), ignored -> new ArrayList<>())
                        .add(tradeMapper.toEvent(trade, market));
            } catch (RuntimeException exception) {
                log.warn("Ignoring invalid Kraken Futures trade symbol={} reason={}", symbol, exception.getMessage());
            }
        });
        byMarket.values().forEach(trades -> recentTradesEventPublisher.publish(
                recentTradesStateService.addBatch(trades)));
    }

    private List<com.hope.trading.market_data.kraken.dto.orderbook.KrakenOrderBookLevel> levels(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        return java.util.stream.StreamSupport.stream(node.spliterator(), false).map(level ->
                new com.hope.trading.market_data.kraken.dto.orderbook.KrakenOrderBookLevel(
                        level.isObject() ? decimal(level, PRICE_FIELD) : decimal(level, 0),
                        level.isObject() ? decimal(level, QTY_FIELD) : decimal(level, 1))).toList();
    }

    private java.math.BigDecimal decimal(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull()
                ? new java.math.BigDecimal(node.get(field).asString()) : null;
    }

    private java.math.BigDecimal decimal(JsonNode node, int index) {
        return node != null && node.isArray() && node.size() > index
                ? new java.math.BigDecimal(node.get(index).asString()) : null;
    }

    private Instant timestamp(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value.isNumber()) return Instant.ofEpochMilli(value.asLong());
        try { return Instant.parse(value.asString()); } catch (RuntimeException ignored) { return null; }
    }

    private enum Product { SPOT, FUTURES }

    private Product product(Market market) {
        return isFutures(market) ? Product.FUTURES : Product.SPOT;
    }

    private boolean isFutures(Market market) {
        return market != null && market.getProductType() == InstrumentProductType.LINEAR_PERPETUAL;
    }

    private void validateProviderSymbols(List<Market> markets) {
        if (markets == null) return;
        markets.stream()
                .filter(this::isFutures)
                .filter(market -> market.getProviderSymbol() == null
                        || market.getProviderSymbol().isBlank())
                .findFirst()
                .ifPresent(market -> {
                    throw new IllegalArgumentException(
                            "Kraken Futures market requires providerSymbol");
                });
    }

    private int streamSubscriptionCount(Market market, MarketStreamRequest request) {
        AtomicInteger count = streamSubscriptionCounts.get(streamSubscriptionKey(market, request));
        return count == null ? 0 : count.get();
    }

    private String streamSubscriptionKey(Market market, MarketStreamRequest request) {
        String symbol = subscriptionKey(market, product(market));
        Integer interval = request.parameters() == null ? null : request.parameters().interval();
        Integer depth = request.parameters() == null ? null : request.parameters().depth();
        return symbol + "|" + request.type() + "|" + interval + "|" + depth;
    }

    private String subscriptionKey(Market market, Product product) {
        return subscriptionKey(product == Product.FUTURES ? market.getProviderSymbol() : market.getSymbol(), product);
    }

    private String subscriptionKey(String symbol, Product product) {
        return product.name() + ":" + normalize(symbol);
    }

    private String futuresFeed(MarketStreamType type) {
        return switch (type) {
            case TICKER -> TICKER_FEED;
            case ORDER_BOOK -> "book";
            case TRADES -> TRADE_FEED;
            case OHLC -> throw new UnsupportedOperationException("Kraken Futures OHLC WebSocket feed is unsupported");
        };
    }

    private String url(Product product) {
        return product == Product.FUTURES
                ? krakenProperties.getFuturesWebsocket() : krakenProperties.getWebsocket();
    }

    private WebSocketSession session(Product product) {
        return product == Product.FUTURES ? futuresSession.get() : session.get();
    }

    private void setSession(Product product, WebSocketSession value) {
        if (product == Product.FUTURES) futuresSession.set(value); else session.set(value);
    }

    private Disposable subscription(Product product) {
        return product == Product.FUTURES ? futuresConnectionSubscription.get() : connectionSubscription.get();
    }

    private void setSubscription(Product product, Disposable value) {
        if (product == Product.FUTURES) futuresConnectionSubscription.set(value); else connectionSubscription.set(value);
    }

    private record ActiveSubscription(Market market, MarketStreamRequest request) {
        // State-only record; no additional behavior is required.
    }

    private Sinks.Many<String> outbound(Product product) {
        return product == Product.FUTURES ? futuresOutboundMessages : outboundMessages;
    }





    }
