package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.domain.Ratio;
import java.util.Objects;

public record DerivedMetrics(
        Money remainingRisk, Money portfolioHeat, Ratio riskUtilization,
        Ratio positionRiskRatio, Ratio exposureRatio, Ratio dailyDrawdownRatio
) {
    public DerivedMetrics {
        Objects.requireNonNull(remainingRisk); Objects.requireNonNull(portfolioHeat);
        Objects.requireNonNull(riskUtilization); Objects.requireNonNull(positionRiskRatio);
        Objects.requireNonNull(exposureRatio); Objects.requireNonNull(dailyDrawdownRatio);
    }
}
