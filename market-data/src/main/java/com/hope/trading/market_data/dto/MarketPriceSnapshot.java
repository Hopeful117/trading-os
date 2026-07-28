package com.hope.trading.market_data.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketPriceSnapshot(
        UUID marketId,
        String symbol,
        BigDecimal lastPrice,
        BigDecimal bid,
        BigDecimal ask,
        boolean tradable,
        Instant occurredAt,
        MarketPriceSnapshotStatus status
) {
}
