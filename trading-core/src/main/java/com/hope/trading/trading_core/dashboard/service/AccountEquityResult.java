package com.hope.trading.trading_core.dashboard.service;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountEquityResult(
        BigDecimal equity,
        BigDecimal calculatedEquity,
        String source,
        boolean divergent,
        String valuationStatus,
        Instant valuationTimestamp,
        String valuationPolicyVersion
) {
}
