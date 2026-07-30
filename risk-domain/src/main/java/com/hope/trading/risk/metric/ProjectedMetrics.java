package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import java.util.Objects;

public record ProjectedMetrics(
        Money exposure, Money drawdown, Money margin, Money portfolioHeat,
        ProjectedPortfolioState portfolioState
) {
    public ProjectedMetrics {
        Objects.requireNonNull(exposure); Objects.requireNonNull(drawdown);
        Objects.requireNonNull(margin); Objects.requireNonNull(portfolioHeat);
        Objects.requireNonNull(portfolioState);
    }
}
