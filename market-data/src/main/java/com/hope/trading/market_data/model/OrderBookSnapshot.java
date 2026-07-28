package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderBookSnapshot(
        UUID marketId,
        MarketProvider provider,
        String symbol,
        int depth,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        BigDecimal spread,
        BigDecimal bidVolume,
        BigDecimal askVolume,
        BigDecimal imbalance,
        Instant occurredAt
) implements MarketEvent {
    public OrderBookSnapshot {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }

    @Override
    public MarketStreamType streamType() {
        return MarketStreamType.ORDER_BOOK;
    }
}
