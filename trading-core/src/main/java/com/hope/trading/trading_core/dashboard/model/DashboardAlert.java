package com.hope.trading.trading_core.dashboard.model;

import java.time.Instant;
import java.util.UUID;

public record DashboardAlert(
        String code,
        DashboardAlertSeverity severity,
        String title,
        String message,
        UUID marketId,
        String positionId,
        Instant occurredAt
) {
}
