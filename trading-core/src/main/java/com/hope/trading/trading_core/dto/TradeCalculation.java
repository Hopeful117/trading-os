package com.hope.trading.trading_core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class TradeCalculation {
    private BigDecimal entryPrice;

    private BigDecimal riskAmount;

    private BigDecimal rewardAmount;

    private BigDecimal riskRewardRatio;

    private BigDecimal positionSize;
}
