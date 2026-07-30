package com.hope.trading.market_intelligence.domain.tradeplan;

import java.math.BigDecimal;
import java.util.Objects;

public record TakeProfit(BigDecimal price, BigDecimal allocationPercent) {
    public TakeProfit {
        if (Objects.requireNonNull(price).signum() <= 0) {
            throw new IllegalArgumentException("Target price must be positive");
        }
        allocationPercent = Objects.requireNonNull(allocationPercent);
        if (allocationPercent.signum() <= 0
                || allocationPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Target allocation must be in ]0,100]");
        }
    }
}
