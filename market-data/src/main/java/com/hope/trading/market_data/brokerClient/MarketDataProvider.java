package com.hope.trading.market_data.brokerClient;

import com.hope.trading.market_data.model.Market;

import java.util.List;

public interface MarketDataProvider {
    List<Market> getMarkets();

}
