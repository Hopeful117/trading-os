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
        Instant occurredAt,
        String sourceId,
        Instant fetchedAt,
        String providerSymbol


)implements MarketEvent {
    public TickerEvent(UUID marketId, MarketProvider provider, String symbol,
                       BigDecimal bid, BigDecimal ask, BigDecimal last,
                       BigDecimal volume, Instant occurredAt) {
        this(marketId, provider, symbol, bid, ask, last, volume, occurredAt,
                defaultSourceId(marketId, provider, symbol), occurredAt, null);
    }

    public static String defaultSourceId(UUID marketId, MarketProvider provider, String symbol) {
        return provider + ":" + marketId + ":" + symbol + ":ticker";
    }

    @Override
    public MarketStreamType streamType(){
        return MarketStreamType.TICKER;
    }
}
