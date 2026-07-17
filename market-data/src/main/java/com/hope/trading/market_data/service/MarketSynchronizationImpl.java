package com.hope.trading.market_data.service;

import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.model.Market;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketSynchronizationImpl implements MarketSynchronization{

    private final MarketDataProvider marketDataProvider;

    @Override
    public List<Market> synchronizeMarkets() {
        return marketDataProvider.getMarkets();
    }
}
