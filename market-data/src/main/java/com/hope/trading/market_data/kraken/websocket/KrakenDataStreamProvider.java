package com.hope.trading.market_data.kraken.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hope.trading.market_data.brokerClient.MarketDataStreamProvider;
import com.hope.trading.market_data.kraken.config.KrakenProperties;
import com.hope.trading.market_data.kraken.dto.KrakenTickerMessage;
import com.hope.trading.market_data.kraken.helper.KrakenTickerMapper;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketDataEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;

@Component
@Slf4j
public class KrakenDataStreamProvider implements MarketDataStreamProvider {
    private final ReactorNettyWebSocketClient client;
    private final KrakenProperties krakenProperties;
    private final ObjectMapper objectMapper;
    private final KrakenTickerMapper tickerMapper;
    private WebSocketSession session;


    public KrakenDataStreamProvider(KrakenProperties krakenProperties, ObjectMapper objectMapper, KrakenTickerMapper tickerMapper) {
        this.objectMapper = objectMapper;
        this.tickerMapper = tickerMapper;
        this.client = new ReactorNettyWebSocketClient();
        this.krakenProperties = krakenProperties;

    }


    @Override
    public void connect() {
        client.execute(
                URI.create(krakenProperties.getWebsocket()),
                webSocketSession -> {
                    this.session = webSocketSession;

                    log.info("Connected to Kraken websocket");
                    subscribeTicker("BTC/USD");
                    return session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(this::handleMessage)
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

    private void subscribeTicker(String symbol) {

        String request = """
            {
              "method": "subscribe",
              "params": {
                "channel": "ticker",
                "symbol": [
                  "%s"
                ]
              }
            }
            """.formatted(symbol);


        session.send(
                Mono.just(
                        session.textMessage(request)
                )
        ).subscribe();

    }
    private void handleMessage(String message){

        if (!message.contains("\"channel\":\"ticker\"")) {
            return;
        }


        try {

            KrakenTickerMessage tickerMessage =
                    objectMapper.readValue(
                            message,
                            KrakenTickerMessage.class
                    );


            tickerMessage.getData()
                    .forEach(data -> {

                        MarketDataEvent event =
                                tickerMapper.toEvent(data);


                        log.debug(
                                "MarketDataEvent received : {}",
                                event
                        );

                    });


        } catch (Exception e) {

            log.error(
                    "Error parsing Kraken ticker message",
                    e
            );

        }

    }





    }
