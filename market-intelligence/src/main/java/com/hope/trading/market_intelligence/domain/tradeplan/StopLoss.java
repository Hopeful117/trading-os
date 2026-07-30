package com.hope.trading.market_intelligence.domain.tradeplan;

import java.math.BigDecimal;
import java.util.Objects;

public record StopLoss(BigDecimal price, String rationale) {
    public StopLoss {
        if (Objects.requireNonNull(price).signum() <= 0) {
            throw new IllegalArgumentException("Stop loss must be positive");
        }
        rationale = required(rationale);
    }
    private static String required(String value) {
        String result = Objects.requireNonNull(value).trim();
        if (result.isEmpty()) throw new IllegalArgumentException("Stop rationale is required");
        return result;
    }
}
