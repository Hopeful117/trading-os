package com.hope.trading.trading_core.dashboard.integration;

import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotDto;
import org.springframework.stereotype.Component;

@Component
public class MarketDataDashboardMapper {
    public MarketPriceFact toFact(MarketPriceSnapshotDto dto) {
        return new MarketPriceFact(
                dto.marketId(),
                dto.symbol(),
                dto.lastPrice(),
                dto.tradable(),
                dto.occurredAt(),
                dto.status()
        );
    }
}
