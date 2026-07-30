package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.domain.Ratio;

public record RiskMetrics(
        Money balance, Money equity, Money floatingPnl, Money closedPnl,
        Money margin, Money freeMargin,
        Money projectedExposure, Money projectedDrawdown, Money projectedMargin,
        Money remainingRisk, Money portfolioHeat, Ratio riskUtilization,
        Ratio positionRiskRatio, Ratio exposureRatio, Ratio dailyDrawdownRatio
) {}
