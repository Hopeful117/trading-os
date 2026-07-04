package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.TimeUtils;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradeAnalyticsServiceImpl implements TradeAnalyticsService {
    private final TradeRepository tradeRepository;
    private final AccountRepository accountRepository;

    @Override
    public BigDecimal getTodayPnL(UUID accountId) {
        return tradeRepository.findByAccountIdAndOpenedAtBetween(accountId, TimeUtils.startOfDay(), TimeUtils.endOfDay()).stream()
                .map(Trade::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal getTotalPnL(UUID accountId) {
        return tradeRepository.findAllByAccountId(accountId).stream()
                .map(Trade::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal getOpenPnL(UUID accountId) {
        return tradeRepository.findAllByAccountId(accountId).stream()
                .filter(trade -> trade.getClosedAt() == null)
                .map(Trade::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal getClosedPnL(UUID accountId) {
        return tradeRepository.findAllByAccountId(accountId).stream()
                .filter(trade -> trade.getClosedAt() != null)
                .map(Trade::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public int getTodayTradeCount(UUID accountId) {
        return tradeRepository.findByAccountIdAndOpenedAtBetween(accountId, TimeUtils.startOfDay(), TimeUtils.endOfDay()).size();
    }

    @Override
    public int getOpenTradeCount(UUID accountId) {
        return tradeRepository.countByAccountIdAndClosedAtIsNull(accountId);
    }

    @Override
    public int getClosedTradeCount(UUID accountId) {
        return tradeRepository.countByAccountIdAndClosedAtIsNotNull(accountId);
    }

    @Override
    public List<Trade> getTodayTrades(UUID accountId) {
        return tradeRepository.findByAccountIdAndOpenedAtBetween(accountId, TimeUtils.startOfDay(), TimeUtils.endOfDay());
    }

    @Override
    public long getWinningTradeCount(UUID accountId) {
        Assert.isTrue(accountRepository.existsById(accountId), "Account not found with ID: " + accountId);
        List<Trade> closedTrades = getClosedTrades(accountId);
        return closedTrades.stream()
                .filter(trade -> trade.getPnl() != null && trade.getPnl().compareTo(BigDecimal.ZERO) > 0)
                .count();
    }

    @Override
    public long getLosingTradeCount(UUID accountId) {
        Assert.isTrue(accountRepository.existsById(accountId), "Account not found with ID: " + accountId);
        List<Trade> closedTrades = getClosedTrades(accountId);
        return closedTrades.stream()
                .filter(trade -> trade.getPnl() != null && trade.getPnl().compareTo(BigDecimal.ZERO) < 0)
                .count();
    }

    @Override
    public double getWinRate(UUID accountId) {
        Assert.isTrue(accountRepository.existsById(accountId), "Account not found with ID: " + accountId);
        List<Trade> closedTrades = getClosedTrades(accountId);
        long winningTradeCount = closedTrades.stream()
                .filter(trade -> trade.getPnl() != null && trade.getPnl().compareTo(BigDecimal.ZERO) > 0)
                .count();
        long totalTradeCount = closedTrades.size();
        return totalTradeCount > 0 ? (double) winningTradeCount / totalTradeCount : 0.0;
    }

    @Override
    public BigDecimal getAverageWin(UUID accountId) {
        Assert.isTrue(accountRepository.existsById(accountId), "Account not found with ID: " + accountId);
        List<Trade> closedTrades = getClosedTrades(accountId);
        BigDecimal totalWin = closedTrades.stream()
                .filter(trade -> trade.getPnl() != null && trade.getPnl().compareTo(BigDecimal.ZERO) > 0)
                .map(Trade::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long winningTradeCount = closedTrades.stream()
                .filter(trade -> trade.getPnl() != null && trade.getPnl().compareTo(BigDecimal.ZERO) > 0)
                .count();
        return winningTradeCount > 0 ? totalWin.divide(BigDecimal.valueOf(winningTradeCount), RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getAverageLoss(UUID accountId) {
        Assert.isTrue(accountRepository.existsById(accountId), "Account not found with ID: " + accountId);
        List<Trade> closedTrades = getClosedTrades(accountId);
        BigDecimal totalLoss = closedTrades.stream()
                .filter(trade -> trade.getPnl() != null && trade.getPnl().compareTo(BigDecimal.ZERO) < 0)
                .map(Trade::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long losingTradeCount = closedTrades.stream()
                .filter(trade -> trade.getPnl() != null && trade.getPnl().compareTo(BigDecimal.ZERO) < 0)
                .count();
        return losingTradeCount > 0 ? totalLoss.divide(BigDecimal.valueOf(losingTradeCount), RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getCurrentDrawdown(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with ID: " + accountId));
        return account.getBalance().subtract(account.getEquity());
    }

    @Override
    public BigDecimal getCurrentExposure(UUID accountId) {
        Assert.isTrue(accountRepository.existsById(accountId), "Account not found with ID: " + accountId);
        List<Trade> openTrades = getOpenTrades(accountId);
            return openTrades.stream()
                .map(trade -> trade.getEntryPrice().multiply(trade.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal getRiskUsedToday(UUID accountId) {
        Account account= accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with ID: " + accountId));
        BigDecimal maxRiskPerTrade = account.getRules().getMaxRiskPerTrade();
        int todayTradeCount = getTodayTradeCount(accountId);
        return maxRiskPerTrade.multiply(BigDecimal.valueOf(todayTradeCount));
    }

    @Override
    public boolean hasReachedDailyLoss(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with ID: " + accountId));
        BigDecimal dailyLossLimit = account.getRules().getMaxDailyLoss();
        BigDecimal totalLossToday = getTodayPnL(accountId).negate();
        return dailyLossLimit.compareTo(totalLossToday) <= 0;
    }

    @Override
    public List<Trade> getTradesBetween(UUID accountId, Instant start, Instant end) {
        return tradeRepository.findByAccountIdAndOpenedAtBetween(accountId, start, end);
    }

    @Override
    public List<Trade> getOpenTrades(UUID accountId) {
        return tradeRepository.findAll().stream()
                .filter(trade -> trade.getAccount().getAccountId().equals(accountId))
                .filter(trade -> trade.getClosedAt() == null)
                .toList();
    }

    @Override
    public List<Trade> getClosedTrades(UUID accountId) {
        return tradeRepository.findAll().stream()
                .filter(trade -> trade.getAccount().getAccountId().equals(accountId))
                .filter(trade -> trade.getClosedAt() != null)
                .toList();
    }
}
