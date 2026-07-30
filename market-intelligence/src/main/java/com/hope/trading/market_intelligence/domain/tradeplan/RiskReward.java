package com.hope.trading.market_intelligence.domain.tradeplan;

import java.math.*;
import java.util.Objects;

public record RiskReward(BigDecimal ratio) {
    public RiskReward {
        ratio = Objects.requireNonNull(ratio).setScale(2, RoundingMode.HALF_UP);
        if (ratio.signum() <= 0) throw new IllegalArgumentException("Risk/reward must be positive");
    }
}
