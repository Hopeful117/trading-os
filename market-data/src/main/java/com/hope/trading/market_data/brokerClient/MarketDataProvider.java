package com.hope.trading.market_data.brokerClient;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.TickerEvent;

import java.util.List;
import java.util.Optional;

public interface MarketDataProvider {
    List<Market> getMarkets();
    MarketProvider getName();
    Optional<TickerEvent> acquireCurrentSnapshot(Market market);

    List<OhlcEvent> findOhlcHistory(
            Market market,
            OhlcInterval interval,
            int limit
    );

}
