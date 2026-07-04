package com.hope.trading.trading_core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradingStatistics {

    private BigDecimal totalPnL;

    private int todayTrades;

    private long winners;

    private long losers;

    private double winRate;

    private BigDecimal exposure;

    private BigDecimal drawdown;
}
