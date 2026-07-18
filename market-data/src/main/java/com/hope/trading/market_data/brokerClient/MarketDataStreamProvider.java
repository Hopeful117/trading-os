package com.hope.trading.market_data.brokerClient;

import com.hope.trading.market_data.model.Market;

import java.util.List;

public interface MarketDataStreamProvider {
    void connect();

    void disconnect();

    void subscribe(List<Market> markets);

    void unsubscribe(List<Market> markets);

}
