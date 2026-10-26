package com.hope.trading.market_data.brokerclient;

import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.helper.MarketProvider;
import reactor.core.publisher.Mono;

import java.util.List;

public interface MarketDataStreamProvider {
    default MarketProvider provider() { return null; }

    Mono<Void> connect();

    Mono <Void> disconnect();

    Mono<Void> subscribe(List<Market> markets, MarketStreamRequest request);

    Mono<Void> unsubscribe(List<Market> markets,MarketStreamRequest request);

}
