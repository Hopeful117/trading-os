package com.hope.trading.market_data.model;

import java.math.BigDecimal;

public record OrderBookLevel(
        BigDecimal price,
        BigDecimal quantity
) {
}
