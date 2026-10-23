package com.hope.trading.trading_core.broker.dto;

import java.math.BigDecimal;

public record BrokerAccountFactsV2Dto(String valuationAsset, BigDecimal balance,
                                      BigDecimal equity, BigDecimal usedMargin,
                                      BigDecimal freeMargin) {
}
