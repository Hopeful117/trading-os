package com.hope.trading.market_data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_data.model.MarketDataEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataWebSocketHandler extends TextWebSocketHandler {
    private final MarketDataEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Map<String, Disposable> subscriptions =
            new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) {
        String symbol = extractSymbol(Objects.requireNonNull(session.getUri()));

        log.info(
                "[WS] Frontend connected session={} symbol={}",
                session.getId(),
                symbol
        );

        Disposable subscription =
                eventPublisher.streamBySymbol(symbol)
                        .subscribe(
                                event -> sendEvent(session, event),
                                error -> log.error(
                                        "[WS] Stream error session={}",
                                        session.getId(),
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
            org.springframework.web.socket.@NonNull CloseStatus status
    ) {
        Disposable subscription =
                subscriptions.remove(session.getId());

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
            MarketDataEvent event
    ) {
        if (!session.isOpen()) {
            return;
        }

        try {
            String payload =
                    objectMapper.writeValueAsString(event);

            synchronized (session) {
                session.sendMessage(
                        new TextMessage(payload)
                );
            }
            log.info(
                    "[FRONTEND-WS] sending session={} symbol={} last={}",
                    session.getId(),
                    event.getSymbol(),
                    event.getLast()
            );

            log.debug(
                    "[WS] Frame sent session={} symbol={}",
                    session.getId(),
                    event.getSymbol()
            );

        } catch (Exception exception) {
            log.error(
                    "[WS] Unable to send event session={}",
                    session.getId(),
                    exception
            );
        }
    }



    private String extractSymbol(URI uri) {

        String query = uri.getQuery();

        if (query == null) {
            throw new IllegalArgumentException(
                    "Market symbol is required"
            );
        }

        return Arrays.stream(query.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .filter(parts ->
                        parts.length == 2
                                && parts[0].equals("symbol")
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
                                "Market symbol is required"
                        )
                );
    }
    }

