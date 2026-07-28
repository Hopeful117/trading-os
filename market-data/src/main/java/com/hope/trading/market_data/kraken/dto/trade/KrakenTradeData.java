package com.hope.trading.market_data.kraken.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record KrakenTradeData(
        String symbol,
        String side,
        @JsonProperty("qty")
        BigDecimal quantity,
        BigDecimal price,
        @JsonProperty("ord_type")
        String orderType,
        @JsonProperty("trade_id")
        Long tradeId,
        Instant timestamp
) {
}
