package com.hope.trading.market_data.model;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record MarketConstraints(BigDecimal minimumOrderSize, BigDecimal maximumOrderSize,
                                BigDecimal orderStepSize, BigDecimal minimumCost, BigDecimal tickSize,
                                Integer quantityPrecision, Integer pricePrecision) {
}
