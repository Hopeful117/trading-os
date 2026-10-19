package com.hope.trading.market_data.kraken.websocket;

import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;
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
    Mono<Void> connect(UUID connectionId, URI uri, Function<WebSocketSession, Mono<Void>> sessionHandler);

    default KrakenTransportTerminationEvidence terminationEvidence(UUID connectionId) {
        return KrakenTransportTerminationEvidence.empty();
    }

    default KrakenTransportTerminationEvidence consumeTerminationEvidence(UUID connectionId) {
        KrakenTransportTerminationEvidence evidence = terminationEvidence(connectionId);
        clearTerminationEvidence(connectionId);
        return evidence;
    }

    default void clearTerminationEvidence(UUID connectionId) {
    }
}
