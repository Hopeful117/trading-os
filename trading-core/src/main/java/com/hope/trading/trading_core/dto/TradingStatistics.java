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

    private BigDecimal todayPnL;

    private BigDecimal totalPnL;

    private int todayTrades;

    private int todayTradeCount;

    private int openTradeCount;

    private int closedTradeCount;

    private BigDecimal currentDrawdown;

    private BigDecimal currentExposure;

    private long winners;

    private long losers;

    private double winRate;

    private BigDecimal exposure;

    private BigDecimal drawdown;

    private BigDecimal riskUsedToday;

    private Boolean hasReachedDailyLoss;
}
