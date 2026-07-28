package com.hope.trading.trading_core.dashboard.integration;

import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketPriceFact(
        UUID marketId,
        String symbol,
        BigDecimal price,
        boolean tradable,
        Instant occurredAt,
        MarketPriceSnapshotStatus status
) {
}
