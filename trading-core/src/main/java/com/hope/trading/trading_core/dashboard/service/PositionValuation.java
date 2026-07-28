package com.hope.trading.trading_core.dashboard.service;

import java.math.BigDecimal;

public record PositionValuation(
        BigDecimal pnl,
        BigDecimal pnlPercentage,
        BigDecimal exposure,
        BigDecimal riskAmount,
        BigDecimal riskPercentage
) {
}
