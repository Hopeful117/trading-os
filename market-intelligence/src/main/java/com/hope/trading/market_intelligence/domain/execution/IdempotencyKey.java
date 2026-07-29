package com.hope.trading.market_intelligence.domain.execution;

import java.util.Objects;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        value = Objects.requireNonNull(value, "Idempotency key is required").trim();
        if (value.isEmpty() || value.length() > 200) {
            throw new IllegalArgumentException("Idempotency key must contain 1 to 200 characters");
        }
    }
}
