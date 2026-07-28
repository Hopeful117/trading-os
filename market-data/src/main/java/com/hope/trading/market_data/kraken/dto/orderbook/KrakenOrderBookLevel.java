package com.hope.trading.market_data.kraken.dto.orderbook;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record KrakenOrderBookLevel(
        BigDecimal price,
        @JsonProperty("qty")
        BigDecimal quantity
) {
}
