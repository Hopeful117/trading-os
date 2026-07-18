package com.hope.trading.trading_core.market_data.service;

import com.hope.trading.trading_core.market_data.dto.MarketResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketService {
    List<MarketResponse> findAll();

    Optional<MarketResponse> findById(UUID marketId);

}
