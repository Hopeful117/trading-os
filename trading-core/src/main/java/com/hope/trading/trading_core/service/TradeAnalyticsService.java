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

    long getWinningTradeCount(UUID accountId,String username);
    long getLosingTradeCount(UUID accountId,String username);
    double getWinRate(UUID accountId,String username);
    BigDecimal getAverageWin(UUID accountId,String username);
    BigDecimal getAverageLoss(UUID accountId,String username);


    BigDecimal getCurrentDrawdown(UUID accountId,String username);
    BigDecimal getCurrentExposure(UUID accountId,String username);
    BigDecimal getRiskUsedToday(UUID accountId);
    boolean hasReachedDailyLoss(UUID accountId);

    List<Trade> getTradesBetween(
            UUID accountId,
            Instant start,
            Instant end
    );
    List<Trade> getOpenTrades(UUID accountId);
    List<Trade> getClosedTrades(UUID accountId);

    TradingStatistics getTradingStatistics(UUID accountId,String username);





}
