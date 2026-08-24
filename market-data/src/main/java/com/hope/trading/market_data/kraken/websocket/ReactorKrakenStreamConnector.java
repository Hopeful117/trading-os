package com.hope.trading.market_data.kraken.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Function;

/** Production adapter delegating to the Reactor Netty WebSocket client. */
@Component
public class ReactorKrakenStreamConnector implements KrakenStreamConnector {

    private final ReactorNettyWebSocketClient client =
            new ReactorNettyWebSocketClient();

    @Override
    public Mono<Void> connect(
            URI uri, Function<WebSocketSession, Mono<Void>> sessionHandler) {
        return client.execute(uri, sessionHandler::apply);
    }
}
