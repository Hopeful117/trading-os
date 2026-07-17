package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketService {
    List<Market> findAll();

    Optional<Market> findById(UUID marketId);

    Optional<Market> findByProviderAndSymbol(MarketProvider marketProvider ,String symbol);



}
