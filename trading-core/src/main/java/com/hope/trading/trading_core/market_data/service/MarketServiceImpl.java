package com.hope.trading.trading_core.market_data.service;

import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketServiceImpl implements MarketService{
    private final MarketDataClient marketDataClient;

    @Override
    public List<MarketResponse> findAll() {
        return marketDataClient.findAll();
    }

    @Override
    public Optional<MarketResponse> findById(UUID marketId) {
        return Optional.ofNullable(marketDataClient.findById(marketId));
    }
}
