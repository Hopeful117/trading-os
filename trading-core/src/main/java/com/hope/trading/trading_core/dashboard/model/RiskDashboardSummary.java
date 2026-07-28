package com.hope.trading.trading_core.dashboard.model;

import java.math.BigDecimal;
import java.util.List;

public record RiskDashboardSummary(
        RiskStatus status,
        BigDecimal usedRiskAmount,
        BigDecimal usedRiskPercentage,
        BigDecimal remainingRiskAmount,
        BigDecimal remainingRiskPercentage,
        BigDecimal dailyLossAmount,
        BigDecimal dailyLossPercentage,
        BigDecimal maximumDailyLossPercentage,
        BigDecimal totalDrawdownAmount,
        BigDecimal totalDrawdownPercentage,
        BigDecimal maximumDrawdownPercentage,
        List<RiskRuleDashboardView> rules
) {
    public RiskDashboardSummary {
        rules = List.copyOf(rules);
    }
}
