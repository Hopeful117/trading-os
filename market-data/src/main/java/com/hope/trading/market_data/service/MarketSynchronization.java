package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.Market;

import java.util.List;

public interface MarketSynchronization {
    List<Market> synchronizeMarkets();
}
