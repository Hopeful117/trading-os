package com.hope.trading.market_intelligence.adapter.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketPriceSnapshotResponse(
        UUID marketId,
        String symbol,
        BigDecimal lastPrice,
        BigDecimal bid,
        BigDecimal ask,
        boolean tradable,
        Instant occurredAt,
        String status
) {
}
