package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dashboard.service.DashboardRiskEvaluation;
import com.hope.trading.trading_core.dashboard.model.RiskRuleDashboardView;
import com.hope.trading.trading_core.dashboard.model.RiskStatus;
import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.RiskResult;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Rules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-2: protects the legacy risk decision table used by the trade
 * workflow and the dashboard. A wrong verdict here would authorize trades a
 * trader's own rules forbid — this is the last line before order placement.
 *
 * <p>Decision table (rules expressed as fractions of available balance):</p>
 * <ul>
 *   <li>risk per trade > balance × maxRiskPerTrade → reject;</li>
 *   <li>today PnL below −(balance × maxDailyLoss) → reject;</li>
 *   <li>trades today ≥ maxTradesPerDay → reject;</li>
 *   <li>otherwise allowed.</li>
 * </ul>
 */
class RiskEngineImplTest {

    private final TradingCalculatorService calculator = mock(TradingCalculatorService.class);
    private final TradeAnalyticsService analytics = mock(TradeAnalyticsService.class);
    private final AccountService accountService = mock(AccountService.class);

    private RiskEngineImpl engine;

    private final Account account = new Account();
    private Rules rules;

    @BeforeEach
    void setUp() {
        engine = new RiskEngineImpl(calculator, analytics, accountService);

        rules = new Rules();
        rules.setActive(true);
        rules.setMaxRiskPerTrade(new BigDecimal("0.02"));   // 2 % du solde
        rules.setMaxDailyLoss(new BigDecimal("0.05"));      // 5 % du solde
        rules.setMaxTotalDrawdown(new BigDecimal("0.10"));
        rules.setMaxTradesPerDay(null);                     // pas de limite par défaut
        account.setRules(rules);
    }

    private TradeRequest request(String stopLoss, String quantity) {
        TradeRequest request = new TradeRequest();
        request.setStopLoss(new BigDecimal(stopLoss));
        request.setQuantity(new BigDecimal(quantity));
        return request;
    }

    @Test
    void tradeWithinAllLimitsIsAllowed() {
        when(calculator.calculateTradeRisk(any(), any(), any()))
                .thenReturn(new BigDecimal("100"));
        when(analytics.getTodayPnL(account.getAccountId())).thenReturn(BigDecimal.ZERO);
        when(analytics.getTodayTradeCount(account.getAccountId())).thenReturn(1);

        RiskResult result = engine.assertTradeAllowed(
                account, rules, request("95", "10"),
                new BigDecimal("100"), new BigDecimal("10000"));

        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    void riskPerTradeAboveLimitIsRejected() {
        // 2 % de 10000 = 200 ; risque demandé = 201.
        when(calculator.calculateTradeRisk(any(), any(), any()))
                .thenReturn(new BigDecimal("201"));
        when(analytics.getTodayPnL(account.getAccountId())).thenReturn(BigDecimal.ZERO);
        when(analytics.getTodayTradeCount(account.getAccountId())).thenReturn(0);

        RiskResult result = engine.assertTradeAllowed(
                account, rules, request("90", "10"),
                new BigDecimal("100"), new BigDecimal("10000"));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Max risk per trade exceeded");
    }

    @Test
    void dailyLossBelowNegativeLimitIsRejected() {
        when(calculator.calculateTradeRisk(any(), any(), any()))
                .thenReturn(BigDecimal.ONE);
        // −(5 % de 10000) = −500 ; PnL du jour = −501 → rejet.
        when(analytics.getTodayPnL(account.getAccountId()))
                .thenReturn(new BigDecimal("-501"));
        when(analytics.getTodayTradeCount(account.getAccountId())).thenReturn(0);

        RiskResult result = engine.assertTradeAllowed(
                account, rules, request("99", "1"),
                new BigDecimal("100"), new BigDecimal("10000"));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Daily loss limit exceeded");
    }

    @Test
    void maxTradesPerDayIsEnforcedOnlyWhenConfigured() {
        when(calculator.calculateTradeRisk(any(), any(), any()))
                .thenReturn(BigDecimal.ONE);
        when(analytics.getTodayPnL(account.getAccountId())).thenReturn(BigDecimal.ZERO);
        when(analytics.getTodayTradeCount(account.getAccountId())).thenReturn(3);
        rules.setMaxTradesPerDay(3);

        RiskResult limited = engine.assertTradeAllowed(
                account, rules, request("99", "1"),
                new BigDecimal("100"), new BigDecimal("10000"));

        assertThat(limited.isAllowed()).isFalse();
        assertThat(limited.getMessage()).isEqualTo("Max trades per day exceeded");
    }

    @Test
    void dashboardEvaluationWithoutActiveRulesIsUnavailable() {
        rules.setActive(false);

        DashboardRiskEvaluation evaluation =
                engine.evaluateDashboard(account, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);

        assertThat(evaluation.status()).isEqualTo(RiskStatus.UNAVAILABLE);
        assertThat(evaluation.rules()).isEmpty();
    }

    @Test
    void dashboardAggregatesWorstStatusAcrossRules() {
        DashboardRiskEvaluation breached =
                engine.evaluateDashboard(account,
                        new BigDecimal("5"),   // daily loss 5 % ≥ limite 5 % → BREACHED
                        new BigDecimal("9"),   // drawdown 9 % ≥ 8 % (80 % de 10 %) → WARNING
                        new BigDecimal("1"));  // risque 1 % < 1.6 % → SAFE
        assertThat(breached.status()).isEqualTo(RiskStatus.BREACHED);

        DashboardRiskEvaluation warning =
                engine.evaluateDashboard(account,
                        new BigDecimal("1"), new BigDecimal("9"), new BigDecimal("1"));
        assertThat(warning.status()).isEqualTo(RiskStatus.WARNING);

        DashboardRiskEvaluation safe =
                engine.evaluateDashboard(account,
                        new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"));
        assertThat(safe.status()).isEqualTo(RiskStatus.SAFE);

        List<RiskRuleDashboardView> views = safe.rules();
        assertThat(views).hasSize(3)
                .extracting(RiskRuleDashboardView::code)
                .containsExactly("DAILY_LOSS", "DRAWDOWN", "POSITION_RISK");
    }
}
