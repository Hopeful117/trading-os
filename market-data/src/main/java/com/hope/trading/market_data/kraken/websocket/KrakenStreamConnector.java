package com.hope.trading.market_data.kraken.websocket;

import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Function;

/**
 * Testability boundary (STORY-0020A-3): isolates the WebSocket client
 * infrastructure used by {@link KrakenDataStreamProvider} so connection
 * lifecycle and message dispatch can be exercised offline.
 *
 * <p>This isolates one external dependency; it is not a messaging framework.</p>
 */
public interface KrakenStreamConnector {

    /**
     * Opens a WebSocket connection and hands the established session to the
     * given handler. The returned mono completes when the connection ends.
     */
    Mono<Void> connect(URI uri, Function<WebSocketSession, Mono<Void>> sessionHandler);
}
