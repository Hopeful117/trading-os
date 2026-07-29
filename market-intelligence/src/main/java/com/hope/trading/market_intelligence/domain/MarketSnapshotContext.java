package com.hope.trading.market_intelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketSnapshotContext(
        UUID marketId,
        String symbol,
        BigDecimal lastPrice,
        BigDecimal bid,
        BigDecimal ask,
        boolean tradable,
        Instant occurredAt
) implements ContextPayload {
}
