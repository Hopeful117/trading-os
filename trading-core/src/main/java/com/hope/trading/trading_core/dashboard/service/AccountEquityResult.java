package com.hope.trading.trading_core.dashboard.service;

import java.math.BigDecimal;

public record AccountEquityResult(
        BigDecimal equity,
        BigDecimal calculatedEquity,
        String source,
        boolean divergent
) {
}
