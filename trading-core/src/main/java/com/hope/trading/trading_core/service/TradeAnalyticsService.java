package com.hope.trading.trading_core.service;


import com.hope.trading.trading_core.dto.TradingStatistics;
import com.hope.trading.trading_core.model.Trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TradeAnalyticsService {
    BigDecimal getTodayPnL(UUID accountId);
    BigDecimal getTotalPnL(UUID accountId);
    BigDecimal getOpenPnL(UUID accountId);
    BigDecimal getClosedPnL(UUID accountId);

    int getTodayTradeCount(UUID accountId);
    int getOpenTradeCount(UUID accountId);
    int getClosedTradeCount(UUID accountId);
    List<Trade> getTodayTrades(UUID accountId);

    long getWinningTradeCount(UUID accountId);
    long getLosingTradeCount(UUID accountId);
    double getWinRate(UUID accountId);
    BigDecimal getAverageWin(UUID accountId);
    BigDecimal getAverageLoss(UUID accountId);


    BigDecimal getCurrentDrawdown(UUID accountId);
    BigDecimal getCurrentExposure(UUID accountId);
    BigDecimal getRiskUsedToday(UUID accountId);
    boolean hasReachedDailyLoss(UUID accountId);

    List<Trade> getTradesBetween(
            UUID accountId,
            Instant start,
            Instant end
    );
    List<Trade> getOpenTrades(UUID accountId);
    List<Trade> getClosedTrades(UUID accountId);

    TradingStatistics getTradingStatistics(UUID accountId);





}
