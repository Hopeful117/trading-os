package com.hope.trading.broker_service.brokerClient;

import com.hope.trading.broker_service.dto.*;
import com.hope.trading.broker_service.helper.KrakenMapper;
import com.hope.trading.broker_service.httpClient.KrakenHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
@RequiredArgsConstructor
public class KrakenApiClient implements BrokerProvider {
    private final KrakenHttpClient httpClient;
    private final KrakenMapper krakenMapper;

    @Override
    public String getBrokerName() {
        return "KRAKEN";
    }

    @Override
    public String getBaseCurrency(){
        return "EUR";
    }

    @Override
    public AccountBalance getBalance() {
        KrakenAccountBalanceResponse response = httpClient.getBalances();
        return krakenMapper.toAccountBalance(response);
    }

    @Override
    public MarketPrice getMarketPrice(String symbol) {
        KrakenTickerResponse response = httpClient.getTicker(symbol);
        return krakenMapper.toMarketPrice(response,symbol);
    }

    @Override
    public List<Position> getOpenPositions() {
        KrakenOpenPositionResponse response = httpClient.getOpenPositions();

        if (response.getResults() == null || response.getResults().isEmpty()) {
            return List.of();
        }

        return response.getResults()
                .entrySet()
                .stream()
                .map(entry ->
                        krakenMapper.toPosition(
                                entry.getKey(),
                                entry.getValue()
                        )
                )
                .toList();
    }
}
