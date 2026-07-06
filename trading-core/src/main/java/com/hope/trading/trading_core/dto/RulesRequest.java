package com.hope.trading.trading_core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RulesRequest {
    private UUID rulesId;

    private String name;

    private boolean active;

    private BigDecimal maxRiskPerTrade;

    private BigDecimal maxDailyLoss;

    private BigDecimal maxTotalDrawdown;

    private Integer maxTradesPerDay;

    private Integer cooldownMinutesBetweenTrades;

    private BigDecimal maxLeverage;

    private String allowedSessions;
}
