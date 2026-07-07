package com.hope.trading.trading_core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TradingStatistics {

    // PnL
    private BigDecimal todayPnL;
    private BigDecimal totalPnL;

    // Activité
    private int todayTradeCount;
    private int openTradeCount;
    private int closedTradeCount;

    // Performance
    private long winners;
    private long losers;
    private double winRate;
    private BigDecimal averageWin;
    private BigDecimal averageLoss;

    // Risque
    private BigDecimal currentDrawdown;
    private BigDecimal currentExposure;
    private BigDecimal riskUsedToday;
    private Boolean hasReachedDailyLoss;
}
