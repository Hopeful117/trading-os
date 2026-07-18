package com.hope.trading.market_data.config;

import com.hope.trading.market_data.kraken.websocket.KrakenDataStreamProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TestRunner implements CommandLineRunner {
    private final KrakenDataStreamProvider provider;

    @Override
    public void run(String...args){

        provider.connect();

    }
}
