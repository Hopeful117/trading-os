package com.hope.trading.broker_service.apiClient;

import com.hope.trading.broker_service.dto.AccountBalance;
import com.hope.trading.broker_service.dto.MarketPrice;
import com.hope.trading.broker_service.dto.Position;


import java.util.List;

public interface BrokerProvider {
    AccountBalance getAccount();

    MarketPrice getMarketPrice(String symbol);

    List<Position> getOpenPositions();

}
