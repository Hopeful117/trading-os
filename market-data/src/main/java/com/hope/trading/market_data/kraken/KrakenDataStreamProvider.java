package com.hope.trading.market_data.kraken;

import com.hope.trading.market_data.brokerClient.MarketDataStreamProvider;
import com.hope.trading.market_data.model.Market;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import java.net.URI;
import java.util.List;

@Component
@Slf4j
public class KrakenDataStreamProvider implements MarketDataStreamProvider {
    private final ReactorNettyWebSocketClient client;
    private final KrakenProperties krakenProperties;


    public KrakenDataStreamProvider(KrakenProperties krakenProperties) {

        this.client = new ReactorNettyWebSocketClient();
        this.krakenProperties = krakenProperties;
    }


    @Override
    public void connect() {
        client.execute(
                URI.create(krakenProperties.getWebsocket()),
                session -> {

                    log.info("Connected to Kraken websocket");

                    return session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(message ->
                                    log.info("Kraken message: {}", message)
                            )
                            .then();

                }
        ).subscribe();


    }

    @Override
    public void disconnect() {

    }

    @Override
    public void subscribe(List<Market> markets) {

    }

    @Override
    public void unsubscribe(List<Market> markets) {

    }
}
