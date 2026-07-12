package com.hope.trading.broker_service.service;

import com.hope.trading.broker_service.dto.AccountBalance;
import com.hope.trading.broker_service.dto.BrokerAccountDto;
import com.hope.trading.broker_service.dto.MarketPrice;
import com.hope.trading.broker_service.dto.Position;

import java.util.List;

public interface BrokerService {

    AccountBalance getBalance();
    List<Position>getOpenPositions();
    MarketPrice getMarketPrice(String symbol);
    BrokerAccountDto getAccount();
}
