package com.hope.trading.market_data.kraken.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/** Production adapter delegating to the Reactor Netty WebSocket client. */
@Component
public class ReactorKrakenStreamConnector implements KrakenStreamConnector {
    private static final int MAX_FRAME_PAYLOAD_LENGTH = 1024 * 1024;
    private final ConcurrentMap<UUID, KrakenTransportTerminationEvidenceAccumulator> evidenceByConnection =
            new ConcurrentHashMap<>();

    @Override
    public Mono<Void> connect(
            UUID connectionId, URI uri, Function<WebSocketSession, Mono<Void>> sessionHandler) {
        KrakenTransportTerminationEvidenceAccumulator evidence =
                new KrakenTransportTerminationEvidenceAccumulator();
        evidenceByConnection.put(connectionId, evidence);
        HttpClient httpClient = HttpClient.create()
                .doOnConnected(connection -> connection.addHandlerLast(
                        new KrakenNettyTerminationObserver(evidence)));
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient(
                httpClient,
                () -> WebsocketClientSpec.builder()
                        .maxFramePayloadLength(MAX_FRAME_PAYLOAD_LENGTH));
        try {
            return client.execute(uri, sessionHandler::apply);
        } catch (RuntimeException exception) {
            evidenceByConnection.remove(connectionId, evidence);
            throw exception;
        }
    }

    @Override
    public KrakenTransportTerminationEvidence terminationEvidence(UUID connectionId) {
        KrakenTransportTerminationEvidenceAccumulator evidence = evidenceByConnection.get(connectionId);
        return evidence == null ? KrakenTransportTerminationEvidence.empty() : evidence.snapshot();
    }

    @Override
    public KrakenTransportTerminationEvidence consumeTerminationEvidence(UUID connectionId) {
        KrakenTransportTerminationEvidenceAccumulator evidence = evidenceByConnection.get(connectionId);
        if (evidence == null) return KrakenTransportTerminationEvidence.empty();
        KrakenTransportTerminationEvidence snapshot = evidence.snapshotAndFinalize();
        evidenceByConnection.remove(connectionId, evidence);
        return snapshot;
    }

    @Override
    public void clearTerminationEvidence(UUID connectionId) {
        evidenceByConnection.remove(connectionId);
    }
}
