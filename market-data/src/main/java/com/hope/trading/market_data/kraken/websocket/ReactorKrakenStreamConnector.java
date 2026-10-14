package com.hope.trading.market_data.kraken.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Function;

/** Production adapter delegating to the Reactor Netty WebSocket client. */
@Component
public class ReactorKrakenStreamConnector implements KrakenStreamConnector {
    private static final int MAX_FRAME_PAYLOAD_LENGTH = 1024 * 1024;

    private final ReactorNettyWebSocketClient client =
            new ReactorNettyWebSocketClient(
                    HttpClient.create(),
                    () -> WebsocketClientSpec.builder()
                            .maxFramePayloadLength(MAX_FRAME_PAYLOAD_LENGTH)
            );

    @Override
    public Mono<Void> connect(
            URI uri, Function<WebSocketSession, Mono<Void>> sessionHandler) {
        return client.execute(uri, sessionHandler::apply);
    }
}
