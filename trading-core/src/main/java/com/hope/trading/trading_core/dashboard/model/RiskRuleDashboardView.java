package com.hope.trading.trading_core.dashboard.model;

import java.math.BigDecimal;

public record RiskRuleDashboardView(
        String code,
        String label,
        BigDecimal limit,
        BigDecimal currentValue,
        RiskStatus status
) {
}
