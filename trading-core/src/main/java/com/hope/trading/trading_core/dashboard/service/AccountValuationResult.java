package com.hope.trading.trading_core.dashboard.service;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountValuationResult(
        BigDecimal value,
        String status,
        Instant valuationTimestamp,
        String policyVersion,
        String warning
) {
    public static AccountValuationResult unavailable(String warning) {
        return new AccountValuationResult(null, "UNAVAILABLE", null, null, warning);
    }
}
