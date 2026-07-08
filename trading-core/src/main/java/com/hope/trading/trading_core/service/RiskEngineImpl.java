package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.RiskResult;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Rules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEngineImpl implements RiskEngine {
    private final TradingCalculatorService tradingCalculatorService;
    private final TradeAnalyticsService tradeAnalyticsService;
    private final AccountService accountService;

    @Override
    public RiskResult assertTradeAllowed(Account account, Rules rules, TradeRequest tradeRequest, BigDecimal entryPrice, BigDecimal availableBalance) {
        BigDecimal riskAmount =
                tradingCalculatorService.calculateTradeRisk(
                        entryPrice,
                        tradeRequest.getStopLoss(),
                        tradeRequest.getQuantity()
                );


        BigDecimal todayPnl =
                tradeAnalyticsService.getTodayPnL(
                        account.getAccountId()
                );


        int tradesToday =
                tradeAnalyticsService.getTodayTradeCount(
                        account.getAccountId()
                );


        if (riskAmount.compareTo(
                availableBalance.multiply(
                        rules.getMaxRiskPerTrade()
                )
        ) > 0) {

            return RiskResult.reject(
                    "Max risk per trade exceeded"
            );
        }


        if (todayPnl.compareTo(
                availableBalance.multiply(
                        rules.getMaxDailyLoss()
                ).negate()
        ) < 0) {

            return RiskResult.reject(
                    "Daily loss limit exceeded"
            );
        }


        if (rules.getMaxTradesPerDay() != null &&
                tradesToday >= rules.getMaxTradesPerDay()) {

            return RiskResult.reject(
                    "Max trades per day exceeded"
            );
        }


        return RiskResult.allowed();

    }
}

