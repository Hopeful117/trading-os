package com.hope.trading.market_data.kraken.websocket;

import com.hope.trading.market_data.brokerClient.MarketDataStreamProvider;
import com.hope.trading.market_data.kraken.config.KrakenProperties;
import com.hope.trading.market_data.kraken.dto.*;
import com.hope.trading.market_data.kraken.helper.KrakenTickerMapper;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketDataEvent;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.service.MarketDataEventPublisher;
import com.hope.trading.market_data.service.TickerEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
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
    private Mono<Void>connection;
    private final ConcurrentMap<String, Market> subscribedMarkets =
            new ConcurrentHashMap<>();
    private volatile WebSocketSession session;
    private volatile reactor.core.Disposable connectionSubscription;
    private final java.util.concurrent.atomic.AtomicBoolean connecting =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private final Sinks.Many<String> outboundMessages =
            Sinks.many()
                    .unicast()
                    .onBackpressureBuffer();

    private final TickerEventPublisher tickerEventPublisher;

    public KrakenDataStreamProvider(KrakenProperties krakenProperties, ObjectMapper objectMapper, KrakenTickerMapper tickerMapper, MarketDataEventPublisher eventPublisher, TickerEventPublisher tickerEventPublisher) {
        this.objectMapper = objectMapper;
        this.tickerMapper = tickerMapper;
        this.tickerEventPublisher = tickerEventPublisher;

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
                                KrakenChannel.TICKER,
                                symbols
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
                KrakenChannel.TICKER,
                symbols
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

        log.info("[KRAKEN-RAW] {}", message);

        try {
            var root = objectMapper.readTree(message);

            if (!root.has("channel")
                    || !"ticker".equals(root.get("channel").asString())) {
                return;
            }

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

                        String symbol =
                                normalize(data.getSymbol());

                        Market market =
                                subscribedMarkets.get(symbol);

                        if (market == null) {
                            log.warn(
                                    "Ignoring Kraken ticker without subscribed market symbol={}",
                                    symbol
                            );
                            return;
                        }

                        TickerEvent event =
                                tickerMapper.toEvent(
                                        data,
                                        market
                                );

                        tickerEventPublisher.publish(event);

                        log.debug(
                                "TickerEvent received: {}",
                                event
                        );
                    });


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
                .filter(java.util.Objects::nonNull)
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
            List<String> symbols
    ) {

        KrakenSubscriptionRequest request =
                new KrakenSubscriptionRequest(
                        method.getValue(),
                        new KrakenSubscriptionParams(
                                channel.getValue(),
                                symbols,
                                "bbo",
                                true
                        )
                );

        final String payload;

        try {
            payload = objectMapper.writeValueAsString(request);
        } catch (Exception exception) {
            return Mono.error(
                    new IllegalStateException(
                            "Unable to serialize Kraken subscription request",
                            exception
                    )
            );
        }

        Sinks.EmitResult result =
                outboundMessages.tryEmitNext(payload);

        if (result.isFailure()) {
            return Mono.error(
                    new IllegalStateException(
                            "Unable to queue Kraken subscription request: "
                                    + result
                    )
            );
        }

        log.info(
                "Kraken {} request queued for channel {} and symbols {}",
                method,
                channel,
                symbols
        );

        return Mono.empty();
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
    private Mono<Void> ensureConnected() {
        WebSocketSession currentSession = this.session;

        if (currentSession != null && currentSession.isOpen()) {
            return Mono.empty();
        }

        return connect();
    }
    private Mono<Void> sendPing(
            WebSocketSession currentSession
    ) {
        if (!currentSession.isOpen()) {
            return Mono.empty();
        }

        String payload = """
            {
              "method": "ping"
            }
            """;

        return currentSession.send(
                Mono.just(
                        currentSession.textMessage(payload)
                )
        ).doOnSuccess(ignored ->
                log.debug("Kraken websocket ping sent")
        );
    }
    private Mono<Void> queueSubscriptionRequest(
            KrakenSubscriptionMethod method,
            KrakenChannel channel,
            List<String> symbols
    ) {

        final String payload;

        try {
            KrakenSubscriptionRequest request =
                    new KrakenSubscriptionRequest(
                            method.getValue(),
                            new KrakenSubscriptionParams(
                                    channel.getValue(),
                                    symbols,
                                    "bbo",
                                    true
                            )
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

        Sinks.EmitResult result =
                outboundMessages.tryEmitNext(payload);

        if (result.isFailure()) {
            return Mono.error(
                    new IllegalStateException(
                            "Unable to queue Kraken subscription request: "
                                    + result
                    )
            );
        }

        log.info(
                "Kraken {} request queued for channel {} and symbols {}",
                method,
                channel,
                symbols
        );

        return Mono.empty();
    }
    private String normalize(String symbol) {
        return symbol
                .trim()
                .toUpperCase(Locale.ROOT);
    }




    }
