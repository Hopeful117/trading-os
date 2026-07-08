package com.hope.trading.broker_service.service;

import com.hope.trading.broker_service.apiClient.BrokerProvider;
import com.hope.trading.broker_service.dto.AccountInfo;
import com.hope.trading.broker_service.dto.MarketPrice;
import com.hope.trading.broker_service.dto.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrokerServiceImpl implements BrokerService {
    private final BrokerProvider brokerProvider;

    @Override
    public AccountInfo getAccount() {
        return brokerProvider.getAccount();
    }

    @Override
    public List<Position> getOpenPositions() {
        return brokerProvider.getOpenPositions();
    }

    @Override
    public MarketPrice getMarketPrice(String symbol) {
        return brokerProvider.getMarketPrice(symbol);
    }
}
