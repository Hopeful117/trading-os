package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.exception.BrokenRulesException;
import com.hope.trading.trading_core.helper.RiskResult;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.repository.RulesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEngineImpl implements RiskEngine {
    private final RulesRepository rulesRepository;
    @Override
    public RiskResult assertTradeAllowed(Account account, Rules rules, BigDecimal riskAmount, BigDecimal todayPnl, int tradesToday) {
        if (riskAmount.compareTo(
                account.getBalance().multiply(rules.getMaxRiskPerTrade())
        ) > 0) {
            return RiskResult.reject("Max risk per trade exceeded");
        }

        if (todayPnl.compareTo(
                account.getBalance().multiply(rules.getMaxDailyLoss()).negate()
        ) < 0) {
            return RiskResult.reject("Daily loss limit exceeded");
        }

        if (rules.getMaxTradesPerDay() != null &&
                tradesToday >= rules.getMaxTradesPerDay()) {
            return RiskResult.reject("Max trades per day exceeded");
        }

        return RiskResult.allowed();
    }

}

