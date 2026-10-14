package com.hope.trading.market_data.kraken.websocket;

import com.hope.trading.market_data.brokerClient.MarketDataStreamProvider;
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
import com.hope.trading.market_data.service.OrderBookStateService;
import com.hope.trading.market_data.service.RecentTradesEventPublisher;
import com.hope.trading.market_data.service.RecentTradesStateService;
import com.hope.trading.market_data.service.TickerEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
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
    private volatile WebSocketSession session;
    private volatile Disposable connectionSubscription;
    private volatile WebSocketSession futuresSession;
    private volatile Disposable futuresConnectionSubscription;
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

    private final TickerEventPublisher tickerEventPublisher;
    private final OhlcEventPublisher ohlcEventPublisher;
    private final KrakenOhlcMapper ohlcMapper;
    private final KrakenOrderBookMapper orderBookMapper;
    private final OrderBookStateService orderBookStateService;
    private final OrderBookEventPublisher orderBookEventPublisher;
    private final KrakenTradeMapper tradeMapper;
    private final RecentTradesStateService recentTradesStateService;
    private final RecentTradesEventPublisher recentTradesEventPublisher;
    private final Object outboundEmissionLock =
            new Object();

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
        this.objectMapper = objectMapper;
        this.tickerMapper = tickerMapper;
        this.tickerEventPublisher = tickerEventPublisher;
        this.ohlcEventPublisher = ohlcEventPublisher;
        this.ohlcMapper = ohlcMapper;
        this.orderBookMapper = orderBookMapper;
        this.orderBookStateService = orderBookStateService;
        this.orderBookEventPublisher = orderBookEventPublisher;
        this.tradeMapper = tradeMapper;
        this.recentTradesStateService = recentTradesStateService;
        this.recentTradesEventPublisher = recentTradesEventPublisher;
        this.connector = connector;
        this.krakenProperties = krakenProperties;
    }


    @Override
    public synchronized Mono<Void> connect() {
        return connect(Product.SPOT);
    }

    private synchronized Mono<Void> connect(Product product) {
        if (session(product) != null && session(product).isOpen()) return Mono.empty();
        if (subscription(product) == null || subscription(product).isDisposed()) {
            setSubscription(product, connector.connect(URI.create(url(product)), ws -> {
                setSession(product, ws);
                Mono<Void> outbound = ws.send(outbound(product).asFlux().map(ws::textMessage));
                Mono<Void> inbound = ws.receive().map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(message -> handleMessage(message, product)).then();
                return Mono.when(outbound, inbound);
            }).doOnError(error -> log.error("[KRAKEN] {} WebSocket connection failed", product, error))
                    .doFinally(signal -> {
                        clearRealtimeStates(product);
                        setSession(product, null);
                        setSubscription(product, null);
                    }).subscribe());
        }
        return waitUntilConnected(product)
                .then(replayActiveSubscriptions(product));
    }

    @Override
    public Mono<Void> disconnect() {
            WebSocketSession currentSession = this.session;
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
                    .doFinally(signal -> {
                        this.session = null;
                    });
            WebSocketSession currentFuturesSession = this.futuresSession;
            Mono<Void> closeFutures = currentFuturesSession == null || !currentFuturesSession.isOpen()
                    ? Mono.empty()
                    : currentFuturesSession.close().doFinally(signal -> this.futuresSession = null);
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
                    case "ticker" -> handleFuturesTicker(root);
                    case "book", "book_snapshot" -> handleFuturesBook(root);
                    case "trade" -> handleFuturesTrades(root);
                    default -> { }
                }
                return;
            }

            if (!root.has("channel")) {
                handleControlMessage(root);
                return;
            }

            String channel = root.get("channel").asString()
                    .trim()
                    .toLowerCase();

            switch (channel) {
                case "ticker" -> handleTickerMessage(root, product);
                case "ohlc" -> handleOhlcMessage(root, product);
                case "book", "book_snapshot" -> handleOrderBookMessage(root, product);
                case "trade" -> handleTradeMessage(root, product);

                case "heartbeat" -> {
                }
                default -> {
                }
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
            root.get("data").forEach(data -> handleFuturesTicker(data));
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

        message.data().forEach(data -> {
            Market market = findSubscribedMarket(data.symbol(), product);

            if (market == null) {
                log.warn(
                        "Ignoring Kraken order-book data without subscribed market symbol={}",
                        data.symbol()
                );
                return;
            }

            Set<Integer> depths = orderBookDepthsBySymbol.get(
                    subscriptionKey(data.symbol(), product)
            );

            if (depths == null || depths.isEmpty()) {
                log.warn(
                        "Ignoring Kraken order-book data without active depth symbol={}",
                        data.symbol()
                );
                return;
            }

            List.copyOf(depths).forEach(depth -> {
                OrderBookDelta delta = orderBookMapper.toDelta(
                        data,
                        message.type(),
                        market,
                        depth
                );

                if (message.type() == KrakenMessageType.SNAPSHOT) {
                    OrderBookSnapshot snapshot =
                            orderBookStateService.initialize(delta);
                    orderBookEventPublisher.publish(snapshot);
                    return;
                }

                orderBookStateService.update(delta)
                        .ifPresent(orderBookEventPublisher::publish);
            });
        });
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
        markets.stream()
                .filter(Objects::nonNull)
                .forEach(market -> {
                    Product product = product(market);
                    String symbol = subscriptionKey(market, product);
                    String streamKey = streamSubscriptionKey(market, request);
                    AtomicInteger streamCount = streamSubscriptionCounts.get(streamKey);
                    if (streamCount != null && streamCount.decrementAndGet() <= 0) {
                        streamSubscriptionCounts.remove(streamKey, streamCount);
                        activeSubscriptions.remove(streamKey);
                    }

                    if (request.type()
                            == MarketStreamType.ORDER_BOOK) {
                        Set<Integer> depths =
                                orderBookDepthsBySymbol.get(symbol);
                        ConcurrentMap<Integer, AtomicInteger> depthCounts =
                                orderBookDepthSubscriptionCounts.get(symbol);
                        if (depths != null && depthCounts != null) {
                            Integer depth = request.parameters().depth();
                            AtomicInteger count = depthCounts.get(depth);
                            if (count != null && count.decrementAndGet() <= 0) {
                                depthCounts.remove(depth, count);
                                depths.remove(depth);
                            }
                            if (depths.isEmpty()) {
                                orderBookDepthsBySymbol.remove(symbol, depths);
                            }
                            if (depthCounts.isEmpty()) {
                                orderBookDepthSubscriptionCounts.remove(symbol, depthCounts);
                            }
                        }
                    }
                    if (request.type() == MarketStreamType.TRADES) {
                        AtomicInteger count = tradeSubscriptionCounts.get(symbol);
                        if (count != null && count.decrementAndGet() <= 0) {
                            tradeSubscriptionCounts.remove(symbol, count);
                            tradeSymbols.remove(symbol);
                        }
                    }

                    marketSubscriptionCounts.computeIfPresent(
                            symbol,
                            (ignored, count) -> {
                                if (count.decrementAndGet() <= 0) {
                                    subscribedMarkets.remove(symbol, market);
                                    return null;
                                }
                                return count;
                            }
                    );
                });
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
    private void handleControlMessage(JsonNode root) {
        if (root.has("success")) {
            boolean success = root.get("success").asBoolean();

            if (success) {
                log.info(
                        "Kraken control message succeeded method={}",
                        root.path("method").asString()
                );
            } else {
                log.warn(
                        "Kraken control message failed method={} error={}",
                        root.path("method").asString(),
                        root.path("error").asString()
                );
            }

            return;
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
        String symbol = data.path("product_id").asText(null);
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
        values.forEach(data -> {
            String symbol = data.path("product_id").asText(null);
            Market market = findSubscribedMarket(symbol, Product.FUTURES);
            if (market == null) return;
            Set<Integer> depths = orderBookDepthsBySymbol.get(subscriptionKey(symbol, Product.FUTURES));
            if (depths == null) return;
            List<com.hope.trading.market_data.kraken.dto.orderbook.KrakenOrderBookLevel> bids = levels(data.get("bids"));
            List<com.hope.trading.market_data.kraken.dto.orderbook.KrakenOrderBookLevel> asks = levels(data.get("asks"));
            KrakenMessageType type = "book_snapshot".equalsIgnoreCase(root.path("feed").asText())
                    ? KrakenMessageType.SNAPSHOT : KrakenMessageType.UPDATE;
            var book = new KrakenOrderBookData(symbol, bids, asks,
                    data.has("checksum") ? data.get("checksum").asLong() : null, timestamp(data, "timestamp"));
            depths.forEach(depth -> {
                OrderBookDelta delta = orderBookMapper.toDelta(book, type, market, depth);
                if (type == KrakenMessageType.SNAPSHOT) orderBookEventPublisher.publish(orderBookStateService.initialize(delta));
                else orderBookStateService.update(delta).ifPresent(orderBookEventPublisher::publish);
            });
        });
    }

    private void handleFuturesTrades(JsonNode root) {
        Map<UUID, List<TradeEvent>> byMarket = new LinkedHashMap<>();
        JsonNode values = root.has("data") && root.get("data").isArray()
                ? root.get("data") : objectMapper.createArrayNode().add(root);
        values.forEach(data -> {
            String symbol = data.path("product_id").asText(null);
            Market market = findSubscribedMarket(symbol, Product.FUTURES);
            if (market == null) return;
            try {
                String tradeId = data.has("trade_id")
                        ? data.get("trade_id").asText()
                        : data.path("uid").asText(null);
                var trade = new com.hope.trading.market_data.kraken.dto.trade.KrakenTradeData(
                        symbol, data.path("side").asText(null), decimal(data, "qty"),
                        decimal(data, "price"), data.path("ord_type").asText(null),
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
                        level.isObject() ? decimal(level, "price") : decimal(level, "price", 0),
                        level.isObject() ? decimal(level, "qty") : decimal(level, "qty", 1))).toList();
    }

    private java.math.BigDecimal decimal(JsonNode node, String name) {
        return node != null && node.has(name) && !node.get(name).isNull()
                ? new java.math.BigDecimal(node.get(name).asString()) : null;
    }

    private java.math.BigDecimal decimal(JsonNode node, String name, int index) {
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
            case TICKER -> "ticker";
            case ORDER_BOOK -> "book";
            case TRADES -> "trade";
            case OHLC -> throw new UnsupportedOperationException("Kraken Futures OHLC WebSocket feed is unsupported");
        };
    }

    private String url(Product product) {
        return product == Product.FUTURES
                ? krakenProperties.getFuturesWebsocket() : krakenProperties.getWebsocket();
    }

    private WebSocketSession session(Product product) {
        return product == Product.FUTURES ? futuresSession : session;
    }

    private void setSession(Product product, WebSocketSession value) {
        if (product == Product.FUTURES) futuresSession = value; else session = value;
    }

    private Disposable subscription(Product product) {
        return product == Product.FUTURES ? futuresConnectionSubscription : connectionSubscription;
    }

    private void setSubscription(Product product, Disposable value) {
        if (product == Product.FUTURES) futuresConnectionSubscription = value; else connectionSubscription = value;
    }

    private record ActiveSubscription(Market market, MarketStreamRequest request) {
    }

    private Sinks.Many<String> outbound(Product product) {
        return product == Product.FUTURES ? futuresOutboundMessages : outboundMessages;
    }





    }
