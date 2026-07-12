package com.hope.trading.broker_service.brokerClient;

import com.hope.trading.broker_service.dto.AccountBalance;
import com.hope.trading.broker_service.dto.MarketPrice;
import com.hope.trading.broker_service.dto.Position;


import java.util.List;

public interface BrokerProvider {
    String getBrokerName();

    String getBaseCurrency();

    AccountBalance getBalance();

    MarketPrice getMarketPrice(String symbol);

    List<Position> getOpenPositions();

}
