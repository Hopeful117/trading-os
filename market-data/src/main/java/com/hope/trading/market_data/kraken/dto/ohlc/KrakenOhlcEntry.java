package com.hope.trading.market_data.kraken.dto.ohlc;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record KrakenOhlcEntry(
        String symbol,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Integer trades,
        BigDecimal volume,
        BigDecimal vwap,

        @JsonProperty("interval_begin")
        Instant intervalBegin,

        Integer interval,
        Instant timestamp

) {
}
