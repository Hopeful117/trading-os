package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dashboard.model.RiskStatus;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Rules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RiskEngineDashboardTest {
    private final RiskEngineImpl riskEngine = new RiskEngineImpl(
            mock(TradingCalculatorService.class),
            mock(TradeAnalyticsService.class),
            mock(AccountService.class)
    );

    @Test
    void evaluatesSafeWarningAndBreachedDashboardRules() {
        Account account = Account.builder()
                .rules(Rules.builder()
                        .active(true)
                        .maxRiskPerTrade(new BigDecimal("0.02"))
                        .maxDailyLoss(new BigDecimal("0.05"))
                        .maxTotalDrawdown(new BigDecimal("0.10"))
                        .build())
                .build();

        assertThat(riskEngine.evaluateDashboard(
                account, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE
        ).status()).isEqualTo(RiskStatus.SAFE);
        assertThat(riskEngine.evaluateDashboard(
                account, new BigDecimal("4.5"), BigDecimal.ONE, BigDecimal.ONE
        ).status()).isEqualTo(RiskStatus.WARNING);
        assertThat(riskEngine.evaluateDashboard(
                account, new BigDecimal("5"), BigDecimal.ONE, BigDecimal.ONE
        ).status()).isEqualTo(RiskStatus.BREACHED);
    }
}
