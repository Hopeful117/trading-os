package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TickerEvent(

        UUID marketId,
        MarketProvider provider,
        String symbol,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal last,
        BigDecimal volume,
        Instant occurredAt


)implements MarketEvent {
    @Override
    public MarketStreamType streamType(){
        return MarketStreamType.TICKER;
    }
}
