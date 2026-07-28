package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.RiskResult;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Rules;

import java.math.BigDecimal;
import com.hope.trading.trading_core.dashboard.service.DashboardRiskEvaluation;


public interface RiskEngine {
    RiskResult assertTradeAllowed(Account account, Rules rules, TradeRequest tradeRequest, BigDecimal entryPrice,BigDecimal availableBalance);

    DashboardRiskEvaluation evaluateDashboard(
            Account account,
            BigDecimal dailyLossPercentage,
            BigDecimal drawdownPercentage,
            BigDecimal usedRiskPercentage
    );
}
