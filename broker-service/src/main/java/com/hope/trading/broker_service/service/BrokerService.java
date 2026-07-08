package com.hope.trading.broker_service.service;

import com.hope.trading.broker_service.dto.AccountInfo;
import com.hope.trading.broker_service.dto.MarketPrice;
import com.hope.trading.broker_service.dto.Position;

import java.util.List;

public interface BrokerService {

    AccountInfo getAccount();
    List<Position>getOpenPositions();
    MarketPrice getMarketPrice(String symbol);
}
