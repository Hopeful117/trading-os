package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderBookDelta(
        UUID marketId,
        MarketProvider provider,
        String symbol,
        int depth,
        OrderBookDeltaType type,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        Instant occurredAt,
        Long checksum
) {
    public OrderBookDelta {
        bids = bids == null ? List.of() : List.copyOf(bids);
        asks = asks == null ? List.of() : List.copyOf(asks);
    }
}
