package com.hope.trading.market_data.repository;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MarketRepository extends JpaRepository<Market, UUID> {

    Optional<Market> findByProviderAndSymbol(
            MarketProvider provider,
            String symbol
    );

    boolean existsBySymbol(String symbol);
}
