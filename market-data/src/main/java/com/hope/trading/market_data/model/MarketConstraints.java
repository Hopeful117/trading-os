package com.hope.trading.market_data.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MarketConstraints {
    private final BigDecimal minimumOrderSize;

    private final BigDecimal minimumCost;

    private final BigDecimal tickSize;

    private final Integer quantityPrecision;

    private final Integer pricePrecision;
}
