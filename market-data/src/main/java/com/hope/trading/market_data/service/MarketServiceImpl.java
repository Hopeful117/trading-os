package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.repository.MarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketServiceImpl implements  MarketService {
    private final MarketRepository marketRepository;


    @Override
    public List<Market> findAll() {
        return marketRepository.findAll();
    }

    @Override
    public Optional<Market> findById(UUID marketId) {
        return marketRepository.findById(marketId);
    }

    @Override
    public Optional<Market> findByProviderAndSymbol(MarketProvider marketProvider, String symbol) {
        return marketRepository.findByProviderAndSymbol(marketProvider,symbol);
    }


}
