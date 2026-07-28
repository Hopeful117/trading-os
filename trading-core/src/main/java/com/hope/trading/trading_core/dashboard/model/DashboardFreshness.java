package com.hope.trading.trading_core.dashboard.model;

import java.time.Instant;
import java.util.List;

public record DashboardFreshness(
        DashboardDataStatus status,
        Instant brokerDataAt,
        Instant marketDataAt,
        Instant calculatedAt,
        boolean brokerDataStale,
        boolean marketDataStale,
        List<String> warnings
) {
    public DashboardFreshness {
        warnings = List.copyOf(warnings);
    }
}
