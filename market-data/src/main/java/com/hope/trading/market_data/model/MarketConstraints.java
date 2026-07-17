package com.hope.trading.market_data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record MarketConstraints(BigDecimal minimumOrderSize, BigDecimal minimumCost, BigDecimal tickSize,
                                Integer quantityPrecision, Integer pricePrecision) {
}
