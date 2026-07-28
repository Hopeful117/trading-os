package com.hope.trading.trading_core.dashboard.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketDashboardView(
        UUID marketId,
        String symbol,
        BigDecimal lastPrice,
        boolean tradable,
        Instant occurredAt
) {
}
