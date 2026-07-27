package com.hope.trading.market_data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.TickerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataWebSocketHandler
        extends TextWebSocketHandler {

    private final TickerEventPublisher tickerEventPublisher;
    private final OhlcEventPublisher ohlcEventPublisher;
    private final ObjectMapper objectMapper;

    private final Map<String, Disposable> subscriptions =
            new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) {
        URI uri = Objects.requireNonNull(
                session.getUri()
        );

        String symbol = extractRequiredParameter(
                uri,
                "symbol"
        );

        MarketStreamType streamType =
                extractStreamType(uri);

        log.info(
                "[FRONTEND-WS] subscribing to publisher type={}",
                streamType
        );
        Flux<?> stream = switch (streamType) {

            case TICKER ->
                    tickerEventPublisher
                            .streamBySymbol(symbol);


            case OHLC -> {
                UUID marketId =
                        UUID.fromString(
                                extractRequiredParameter(
                                        uri,
                                        "marketId"
                                )
                        );
                int intervalMinutes =
                        Integer.parseInt(
                                extractRequiredParameter(
                                        uri,
                                        "interval"
                                )
                        );

                OhlcInterval interval =
                        OhlcInterval.fromMinutes(
                                intervalMinutes
                        );

                yield ohlcEventPublisher
                        .streamByMarketAndInterval(
                                marketId,
                                interval
                        );
            }
            case TRADES -> null;
            case ORDER_BOOK -> null;
        };

        log.info(
                "[FRONTEND-WS] session={} symbol={} type={}",
                session.getId(),
                symbol,
                streamType

        );

        assert stream != null;
        Disposable subscription =
                stream.subscribe(
                        event ->
                                sendEvent(
                                        session,
                                        event
                                ),
                        error ->
                                log.error(
                                        "[WS] Stream error session={} symbol={} type={}",
                                        session.getId(),
                                        symbol,
                                        streamType,
                                        error
                                )
                );

        subscriptions.put(
                session.getId(),
                subscription
        );

    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            @NonNull CloseStatus status
    ) {
        Disposable subscription =
                subscriptions.remove(
                        session.getId()
                );

        if (subscription != null) {
            subscription.dispose();
        }

        log.info(
                "[WS] Frontend disconnected session={} status={}",
                session.getId(),
                status
        );
    }

    private void sendEvent(
            WebSocketSession session,
            Object event
    ) {
        if (!session.isOpen()) {
            return;
        }

        try {
            String payload =
                    objectMapper.writeValueAsString(
                            event
                    );

            synchronized (session) {
                session.sendMessage(
                        new TextMessage(payload)
                );
            }

            log.debug(
                    "[WS] Event sent session={} eventType={}",
                    session.getId(),
                    event.getClass().getSimpleName()
            );

        } catch (Exception exception) {
            log.error(
                    "[WS] Unable to send event session={}",
                    session.getId(),
                    exception
            );
        }
    }

    private String extractRequiredParameter(
            URI uri,
            String parameterName
    ) {
        String query = uri.getQuery();

        if (query == null) {
            throw new IllegalArgumentException(
                    "Missing WebSocket query parameters"
            );
        }

        return Arrays.stream(query.split("&"))
                .map(parameter ->
                        parameter.split("=", 2)
                )
                .filter(parts ->
                        parts.length == 2
                                && parts[0].equals(
                                parameterName
                        )
                )
                .map(parts ->
                        URLDecoder.decode(
                                parts[1],
                                StandardCharsets.UTF_8
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Required WebSocket parameter is missing: "
                                        + parameterName
                        )
                );
    }
    private MarketStreamType extractStreamType(URI uri) {
        String rawType =
                extractRequiredParameter(uri, "type");

        try {
            return MarketStreamType.valueOf(
                    rawType.trim().toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported market stream type: " + rawType,
                    exception
            );
        }
    }
}