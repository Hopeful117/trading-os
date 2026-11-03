package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import java.util.Objects;
import java.util.Optional;

public record ObservedMetrics(
        Money balance, Money equity, Money floatingPnl, Optional<Money> closedPnl,
        Money margin, Money freeMargin
) {
    public ObservedMetrics {
        Objects.requireNonNull(balance); Objects.requireNonNull(equity);
        Objects.requireNonNull(floatingPnl); Objects.requireNonNull(closedPnl);
        Objects.requireNonNull(margin); Objects.requireNonNull(freeMargin);
    }
}
