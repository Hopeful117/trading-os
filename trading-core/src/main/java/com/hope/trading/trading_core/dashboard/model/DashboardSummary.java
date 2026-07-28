package com.hope.trading.trading_core.dashboard.model;

import java.time.Instant;
import java.util.List;

public record DashboardSummary(
        AccountDashboardSummary account,
        RiskDashboardSummary risk,
        List<OpenPositionDashboardView> openPositions,
        List<DashboardAlert> alerts,
        List<MarketDashboardView> watchedMarkets,
        DashboardFreshness freshness,
        Instant generatedAt
) {
    public DashboardSummary {
        openPositions = List.copyOf(openPositions);
        alerts = List.copyOf(alerts);
        watchedMarkets = List.copyOf(watchedMarkets);
    }
}
