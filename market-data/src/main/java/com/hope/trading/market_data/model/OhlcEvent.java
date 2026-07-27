package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OhlcEvent(
        UUID marketId,
        MarketProvider provider,
        String symbol,
        OhlcInterval interval,
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
)implements MarketEvent {
    @Override
    public MarketStreamType streamType() {
        return MarketStreamType.OHLC;
    }
}
