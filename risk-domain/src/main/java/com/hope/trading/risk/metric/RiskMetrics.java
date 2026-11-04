package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.domain.Ratio;
import java.util.Optional;

public record RiskMetrics(
        Money balance, Money equity, Money floatingPnl, Optional<Money> closedPnl,
        Optional<Money> margin, Optional<Money> freeMargin,
        Money projectedExposure, Money projectedDrawdown, Optional<Money> projectedMargin,
        Money remainingRisk, Money portfolioHeat, Ratio riskUtilization,
        Ratio positionRiskRatio, Ratio exposureRatio, Ratio dailyDrawdownRatio,
        Optional<Ratio> totalDrawdownRatio
) {}
