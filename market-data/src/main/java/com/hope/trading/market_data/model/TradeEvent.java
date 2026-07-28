package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeEvent(
        UUID marketId,
        MarketProvider provider,
        String symbol,
        String tradeId,
        TradeSide side,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal notional,
        Instant occurredAt
) implements MarketEvent {
    @Override
    public MarketStreamType streamType() {
        return MarketStreamType.TRADES;
    }
}
