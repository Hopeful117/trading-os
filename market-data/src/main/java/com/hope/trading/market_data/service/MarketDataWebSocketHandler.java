package com.hope.trading.market_data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.model.OhlcInterval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataWebSocketHandler
        extends TextWebSocketHandler {

    private static final int MAX_SYMBOL_LENGTH = 64;
    private static final Set<Integer> ALLOWED_ORDER_BOOK_DEPTHS = Set.of(10, 25);

    private final TickerEventPublisher tickerEventPublisher;
    private final OhlcEventPublisher ohlcEventPublisher;
    private final OrderBookEventPublisher orderBookEventPublisher;
    private final RecentTradesEventPublisher recentTradesEventPublisher;
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
        validateSymbol(symbol);

        MarketStreamType streamType =
                extractStreamType(uri);

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
                        parseIntegerParameter(uri, "interval");

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
            case ORDER_BOOK -> {
                UUID marketId = UUID.fromString(
                        extractRequiredParameter(uri, "marketId")
                );
                int depth = parseIntegerParameter(uri, "depth");
                if (!ALLOWED_ORDER_BOOK_DEPTHS.contains(depth)) {
                    throw new IllegalArgumentException(
                            "Unsupported order-book depth: " + depth);
                }

                yield orderBookEventPublisher
                        .streamByMarketAndDepth(marketId, depth);
            }
            case TRADES -> {
                UUID marketId = UUID.fromString(
                        extractRequiredParameter(uri, "marketId")
                );
                yield recentTradesEventPublisher
                        .streamByMarket(marketId);
            }
        };

        log.info(
                "[FRONTEND-WS] session={} symbol={} type={}",
                session.getId(),
                symbol,
                streamType

        );

        Disposable.Swap subscription = Disposables.swap();
        subscriptions.put(session.getId(), subscription);
        try {
            subscription.update(
                    stream.subscribe(
                            event -> sendEvent(session, event),
                            error -> handleStreamError(session, symbol, streamType, error)
                    )
            );
        } catch (RuntimeException exception) {
            subscriptions.remove(session.getId(), subscription);
            subscription.dispose();
            throw exception;
        }

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

        } catch (Exception exception) {
            log.error(
                    "[WS] Unable to send event session={}",
                    session.getId(),
                    exception
            );
        }
    }

    private void handleStreamError(
            WebSocketSession session,
            String symbol,
            MarketStreamType streamType,
            Throwable error
    ) {
        Disposable subscription = subscriptions.remove(session.getId());
        if (subscription != null) {
            subscription.dispose();
        }
        log.error(
                "[WS] Stream error session={} symbol={} type={}",
                session.getId(),
                symbol,
                streamType,
                error
        );
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (Exception closeError) {
            log.warn("[WS] Unable to close failed session={}", session.getId(), closeError);
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

        List<String> values = Arrays.stream(query.split("&"))
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
                .toList();
        if (values.size() != 1 || values.get(0).isBlank()) {
            throw new IllegalArgumentException(
                    values.isEmpty()
                            ? "Required WebSocket parameter is missing: " + parameterName
                            : "WebSocket parameter must appear exactly once: " + parameterName
            );
        }
        return values.get(0);
    }

    private int parseIntegerParameter(URI uri, String parameterName) {
        String raw = extractRequiredParameter(uri, parameterName);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid WebSocket integer parameter: " + parameterName,
                    exception
            );
        }
    }

    private void validateSymbol(String symbol) {
        if (symbol.length() > MAX_SYMBOL_LENGTH
                || symbol.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid WebSocket symbol");
        }
    }

    private MarketStreamType extractStreamType(URI uri) {
        String rawType = extractRequiredParameter(uri, "type");

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
