package com.hope.trading.market_data.kraken.dto.orderbook;

import java.time.Instant;
import java.util.List;

public record KrakenOrderBookData(
        String symbol,
        List<KrakenOrderBookLevel> bids,
        List<KrakenOrderBookLevel> asks,
        Long checksum,
        Instant timestamp,
        Long sequence
) {
    public KrakenOrderBookData(
            String symbol,
            List<KrakenOrderBookLevel> bids,
            List<KrakenOrderBookLevel> asks,
            Long checksum,
            Instant timestamp
    ) {
        this(symbol, bids, asks, checksum, timestamp, null);
    }
}
