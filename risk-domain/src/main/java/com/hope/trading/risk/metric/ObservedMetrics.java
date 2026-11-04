package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import java.util.Objects;
import java.util.Optional;

public record ObservedMetrics(
        Money balance, Money equity, Money floatingPnl, Optional<Money> closedPnl,
        Optional<Money> margin, Optional<Money> freeMargin
) {
    public ObservedMetrics(Money balance, Money equity, Money floatingPnl, Optional<Money> closedPnl,
                           Money margin, Money freeMargin) {
        this(balance, equity, floatingPnl, closedPnl, Optional.of(margin), Optional.of(freeMargin));
    }

    public ObservedMetrics {
        Objects.requireNonNull(balance); Objects.requireNonNull(equity);
        Objects.requireNonNull(floatingPnl); Objects.requireNonNull(closedPnl);
        Objects.requireNonNull(margin); Objects.requireNonNull(freeMargin);
    }
}
