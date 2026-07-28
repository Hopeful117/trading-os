package com.hope.trading.broker_service.service;

import com.hope.trading.broker_service.brokerClient.BrokerProvider;
import com.hope.trading.broker_service.dto.AccountBalance;
import com.hope.trading.broker_service.dto.BrokerAccountDto;
import com.hope.trading.broker_service.dto.MarketPrice;
import com.hope.trading.broker_service.dto.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BrokerServiceImpl implements BrokerService {
    private final BrokerProvider brokerProvider;

    @Override
    public AccountBalance getBalance() {
        return brokerProvider.getBalance();
    }

    @Override
    public List<Position> getOpenPositions() {
        return brokerProvider.getOpenPositions();
    }

    @Override
    public MarketPrice getMarketPrice(String symbol) {
        return brokerProvider.getMarketPrice(symbol);
    }

    @Override
    public BrokerAccountDto getAccount() {

        return BrokerAccountDto.builder()
                .brokerAccountId(brokerProvider.getBrokerName().toLowerCase() + "-default")
                .broker(brokerProvider.getBrokerName())
                .baseCurrency(brokerProvider.getBaseCurrency())
                .accountName(brokerProvider.getBrokerName().toLowerCase() + " account")
                .balances(getBalance())
                .openTrades(getOpenPositions())
                .dataAt(Instant.now())
                .build();
    }
}
