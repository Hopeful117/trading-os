package com.hope.trading.broker_service.apiClient;

import com.hope.trading.broker_service.dto.AccountInfo;
import com.hope.trading.broker_service.dto.KrakenTickerResponse;
import com.hope.trading.broker_service.dto.MarketPrice;
import com.hope.trading.broker_service.dto.Position;
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
    public AccountInfo getAccount() {
        return null;
    }

    @Override
    public MarketPrice getMarketPrice(String symbol) {
        KrakenTickerResponse response = httpClient.getTicker(symbol);
        return krakenMapper.toMarketPrice(response,symbol);
    }

    @Override
    public List<Position> getOpenPositions() {
        return List.of();
    }
}
