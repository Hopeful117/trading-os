package com.hope.trading.market_data.kraken.dto.ohlc;

import java.math.BigDecimal;
import java.time.Instant;

public record KrakenRestOhlcEntry(
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal vwap,
        BigDecimal volume,
        int trades

) {
}
