package com.hope.trading.market_intelligence.adapter.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OhlcResponse(
        UUID marketId,
        String provider,
        String symbol,
        String interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal vwap,
        Integer trades,
        boolean closed,
        Instant occurredAt
) {
}
