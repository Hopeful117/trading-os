package com.hope.trading.trading_core.execution.domain.valueobject;

import java.util.Objects;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        value = Objects.requireNonNull(value).trim();
        if (value.isEmpty() || value.length() > 160) {
            throw new IllegalArgumentException("idempotency key must contain 1 to 160 characters");
        }
    }
}
