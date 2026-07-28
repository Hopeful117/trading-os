package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.RiskResult;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Rules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import com.hope.trading.trading_core.dashboard.model.RiskRuleDashboardView;
import com.hope.trading.trading_core.dashboard.model.RiskStatus;
import com.hope.trading.trading_core.dashboard.service.DashboardRiskEvaluation;
import java.util.List;

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

    @Override
    public DashboardRiskEvaluation evaluateDashboard(
            Account account,
            BigDecimal dailyLossPercentage,
            BigDecimal drawdownPercentage,
            BigDecimal usedRiskPercentage
    ) {
        Rules rules = account.getRules();
        if (rules == null || !rules.isActive()) {
            return new DashboardRiskEvaluation(RiskStatus.UNAVAILABLE, List.of());
        }

        BigDecimal dailyLimit = rules.getMaxDailyLoss().multiply(BigDecimal.valueOf(100));
        BigDecimal drawdownLimit = rules.getMaxTotalDrawdown().multiply(BigDecimal.valueOf(100));
        BigDecimal riskLimit = rules.getMaxRiskPerTrade().multiply(BigDecimal.valueOf(100));

        List<RiskRuleDashboardView> views = List.of(
                rule("DAILY_LOSS", "Perte journalière maximale", dailyLimit, dailyLossPercentage),
                rule("DRAWDOWN", "Drawdown maximal", drawdownLimit, drawdownPercentage),
                rule("POSITION_RISK", "Risque maximal", riskLimit, usedRiskPercentage)
        );
        RiskStatus status = views.stream().anyMatch(view -> view.status() == RiskStatus.BREACHED)
                ? RiskStatus.BREACHED
                : views.stream().anyMatch(view -> view.status() == RiskStatus.WARNING)
                    ? RiskStatus.WARNING
                    : RiskStatus.SAFE;
        return new DashboardRiskEvaluation(status, views);
    }

    private RiskRuleDashboardView rule(
            String code, String label, BigDecimal limit, BigDecimal current
    ) {
        RiskStatus status = current.compareTo(limit) >= 0
                ? RiskStatus.BREACHED
                : current.compareTo(limit.multiply(new BigDecimal("0.80"))) >= 0
                    ? RiskStatus.WARNING
                    : RiskStatus.SAFE;
        return new RiskRuleDashboardView(code, label, limit, current, status);
    }
}
