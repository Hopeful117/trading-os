package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import java.util.Objects;
import java.util.Optional;

public record ProjectedMetrics(
        Money exposure, Money drawdown, Optional<Money> totalDrawdown,
        Optional<Money> margin, Money portfolioHeat,
        ProjectedPortfolioState portfolioState
) {
    public ProjectedMetrics(Money exposure, Money drawdown, Optional<Money> totalDrawdown,
                            Money margin, Money portfolioHeat, ProjectedPortfolioState portfolioState) {
        this(exposure, drawdown, totalDrawdown, Optional.of(margin), portfolioHeat, portfolioState);
    }

    public ProjectedMetrics {
        Objects.requireNonNull(exposure);
        Objects.requireNonNull(totalDrawdown);
        Objects.requireNonNull(margin); Objects.requireNonNull(portfolioHeat);
        Objects.requireNonNull(portfolioState);
    }
}
