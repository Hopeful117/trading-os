package com.hope.trading.trading_core.market_data.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record MarketConstraints(BigDecimal minimumOrderSize, BigDecimal minimumCost, BigDecimal tickSize,
                                Integer quantityPrecision, Integer pricePrecision) {
}

