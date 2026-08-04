package com.hope.trading.market_intelligence.domain.tradeplan;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record RiskBudget(BigDecimal amount, String currency, UUID sourceId, long sourceVersion) {
    public RiskBudget {
        if (Objects.requireNonNull(amount, "amount").signum() <= 0) {
            throw new IllegalArgumentException("Risk budget amount must be positive");
        }
        currency = Objects.requireNonNull(currency, "currency").strip().toUpperCase(Locale.ROOT);
        if (currency.isEmpty()) throw new IllegalArgumentException("Risk budget currency is required");
        Objects.requireNonNull(sourceId, "sourceId");
        if (sourceVersion < 1) throw new IllegalArgumentException("Risk budget source version starts at 1");
    }
}
