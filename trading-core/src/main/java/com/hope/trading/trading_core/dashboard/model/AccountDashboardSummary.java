package com.hope.trading.trading_core.dashboard.model;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountDashboardSummary(
        UUID accountId,
        String accountName,
        String broker,
        String currency,
        BigDecimal balance,
        BigDecimal equity,
        BigDecimal dailyPnl,
        BigDecimal dailyPnlPercentage,
        BigDecimal currentDrawdown,
        BigDecimal currentDrawdownPercentage,
        String equitySource
) {
}
