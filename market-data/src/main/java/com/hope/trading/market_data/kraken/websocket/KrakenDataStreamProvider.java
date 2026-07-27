package com.hope.trading.market_data.kraken.websocket;

import com.hope.trading.market_data.brokerClient.MarketDataStreamProvider;
import com.hope.trading.market_data.kraken.config.KrakenProperties;
import com.hope.trading.market_data.kraken.dto.*;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcMessage;
import com.hope.trading.market_data.kraken.dto.subscription.KrakenSubscriptionMethod;
import com.hope.trading.market_data.kraken.dto.subscription.KrakenSubscriptionParams;
import com.hope.trading.market_data.kraken.dto.subscription.KrakenSubscriptionRequest;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenTickerMessage;
import com.hope.trading.market_data.kraken.helper.KrakenOhlcMapper;
import com.hope.trading.market_data.kraken.helper.KrakenTickerMapper;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.service.OhlcEventPublisher;
import com.hope.trading.market_data.service.TickerEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class KrakenDataStreamProvider implements MarketDataStreamProvider {
    private final ReactorNettyWebSocketClient client;
    private final KrakenProperties krakenProperties;
    private final ObjectMapper objectMapper;
    private final KrakenTickerMapper tickerMapper;
    private final ConcurrentMap<String, Market> subscribedMarkets =
            new ConcurrentHashMap<>();
    private volatile WebSocketSession session;
    private volatile Disposable connectionSubscription;
    private final Sinks.Many<String> outboundMessages =
            Sinks.many()
                    .multicast()
                    .onBackpressureBuffer(
                            OUTBOUND_BUFFER_SIZE,
                            false
                    );
    private static final int OUTBOUND_BUFFER_SIZE = 256;

    private final TickerEventPublisher tickerEventPublisher;
    private final OhlcEventPublisher ohlcEventPublisher;
   private final KrakenOhlcMapper ohlcMapper;
    private final Object outboundEmissionLock =
            new Object();

    public KrakenDataStreamProvider(KrakenProperties krakenProperties, ObjectMapper objectMapper, KrakenTickerMapper tickerMapper, TickerEventPublisher tickerEventPublisher, OhlcEventPublisher ohlcEventPublisher, KrakenOhlcMapper mapper) {
        this.objectMapper = objectMapper;
        this.tickerMapper = tickerMapper;
        this.tickerEventPublisher = tickerEventPublisher;
        this.ohlcEventPublisher = ohlcEventPublisher;
        this.ohlcMapper = mapper;

        this.client = new ReactorNettyWebSocketClient();
        this.krakenProperties = krakenProperties;

    }


    @Override
    public synchronized Mono<Void> connect() {

        if (session != null && session.isOpen()) {
            return Mono.empty();
        }

        if (connectionSubscription == null
                || connectionSubscription.isDisposed()) {

            connectionSubscription =
                    client.execute(
                                    URI.create(krakenProperties.getWebsocket()),
                                    webSocketSession -> {

                                        this.session = webSocketSession;

                                        log.info(
                                                "[KRAKEN] WebSocket connected"
                                        );

                                        Mono<Void> outbound =
                                                webSocketSession.send(
                                                        outboundMessages.asFlux()
                                                                .map(
                                                                        webSocketSession::textMessage
                                                                )
                                                );

                                        Mono<Void> inbound =
                                                webSocketSession.receive()
                                                        .map(
                                                                WebSocketMessage::getPayloadAsText
                                                        )
                                                        .doOnNext(this::handleMessage)
                                                        .then();

                                        /*
                                         * Ce Mono vit aussi longtemps que la socket.
                                         * Il est exécuté via connectionSubscription,
                                         * mais n'est jamais retourné à subscribe().
                                         */
                                        return Mono.when(
                                                outbound,
                                                inbound
                                        );
                                    }
                            )
                            .doOnError(error ->
                                    log.error(
                                            "[KRAKEN] Connection failed",
                                            error
                                    )
                            )
                            .doFinally(signal -> {
                                log.info(
                                        "[KRAKEN] Connection terminated signal={}",
                                        signal
                                );

                                session = null;
                                connectionSubscription = null;
                            })
                            .subscribe();
        }

        /*
         * Ce Mono complète dès que la session est prête.
         */
        return waitUntilConnected();
    }

    @Override
    public Mono<Void> disconnect() {

            WebSocketSession currentSession = this.session;

            if (currentSession == null) {
                return Mono.empty();
            }

            if (!currentSession.isOpen()) {
                this.session = null;
                return Mono.empty();
            }

            return currentSession.close()
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
                        log.debug(
                                "Kraken websocket session cleared ({})",
                                signal
                        );
                    });
        }




    @Override
    public Mono<Void> subscribe(
            List<Market> markets,
            MarketStreamRequest request
    ) {
        List<String> symbols = extractSymbols(markets);

        if (symbols.isEmpty()) {
            return Mono.empty();
        }

        markets.stream()
                .filter(Objects::nonNull)
                .forEach(market ->
                        subscribedMarkets.put(
                                normalize(market.getSymbol()),
                                market
                        )
                );

        return connect()
                .then(
                        sendSubscriptionRequest(
                                                        KrakenSubscriptionMethod.SUBSCRIBE,
                                resolveChannel(request),
                                symbols,
                                request
                                                )
                )
                .doOnError(error ->
                        markets.stream()
                                .filter(Objects::nonNull)
                                .forEach(market ->
                                        subscribedMarkets.remove(
                                                normalize(market.getSymbol()),
                                                market
                                        )
                                )
                );
    }
    @Override
    public Mono<Void> unsubscribe(
            List<Market> markets,
            MarketStreamRequest request
    ) {
        List<String> symbols = extractSymbols(markets);

        if (symbols.isEmpty()) {
            return Mono.empty();
        }

        return sendSubscriptionRequest(
                KrakenSubscriptionMethod.UNSUBSCRIBE,
                resolveChannel(request),
                symbols,
                request
                ).doOnSuccess(ignored ->
                markets.stream()
                        .filter(Objects::nonNull)
                        .forEach(market ->
                                subscribedMarkets.remove(
                                        normalize(market.getSymbol()),
                                        market
                                )
                        )
        );
    }
    private void handleMessage(String message) {
        try {
            var root = objectMapper.readTree(message);

            if (!root.has("channel")) {
                handleControlMessage(root);
                return;
            }

            String channel = root.get("channel").asString()
                    .trim()
                    .toLowerCase();

            switch (channel) {
                case "ticker" -> handleTickerMessage(root);
                case "ohlc" -> handleOhlcMessage(root);

                case "heartbeat" -> log.trace(
                        "Kraken heartbeat received"
                );

                default -> log.debug(
                        "Ignoring unsupported Kraken channel: {}",
                        channel
                );
            }

        } catch (Exception exception) {
            log.error(
                    "Error parsing Kraken websocket message: {}",
                    message,
                    exception
            );
        }
    }


    private List<String> extractSymbols(List<Market> markets) {

        if (markets == null) {
            return List.of();
        }

        return markets.stream()
                .filter(Objects::nonNull)
                .map(Market::getSymbol)
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
            MarketStreamRequest streamRequest
    ) {
        final String payload;

        try {
            KrakenSubscriptionParams parameters =
                    buildSubscriptionParams(
                            channel,
                            symbols,
                            streamRequest
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

        return emitOutboundMessage(payload);
    }

    private Mono<Void> waitUntilConnected() {

        return Mono.defer(() -> {

                    WebSocketSession currentSession = this.session;

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
            MarketStreamRequest request
    ) {
        return switch (request.type()) {
            case TICKER -> new KrakenSubscriptionParams(
                    channel.getValue(),
                    symbols,
                    null,
                    "bbo",
                    true
            );

            case OHLC -> new KrakenSubscriptionParams(
                    channel.getValue(),
                    symbols,
                    request.parameters().interval(),
                    null,
                    true
            );
            case TRADES -> null;
            case ORDER_BOOK -> null;
        };
    }
    private void handleTickerMessage(JsonNode root) {
        if (!root.has("data")
                || !root.get("data").isArray()
                || root.get("data").isEmpty()) {
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
                            data.getSymbol()
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

    private Market findSubscribedMarket(String symbol) {
        return subscribedMarkets.get(symbol);
    }

    private void handleOhlcMessage(JsonNode root) {

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
                            entry.symbol()
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

                    log.debug(
                            "OhlcEvent received: {}",
                            event
                    );
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

        log.debug(
                "Ignoring Kraken control message: {}",
                root
        );
    }
    private Mono<Void> emitOutboundMessage(
            String payload
    ) {
        Sinks.EmitResult result;

        synchronized (outboundEmissionLock) {
            result = outboundMessages.tryEmitNext(payload);
        }

        if (result.isFailure()) {
            return Mono.error(
                    new IllegalStateException(
                            "Unable to queue Kraken subscription request: "
                                    + result
                    )
            );
        }

        log.info(
                "[KRAKEN-QUEUE] payload={} result={}",
                payload,
                result
        );

        return Mono.empty();
    }





    }
