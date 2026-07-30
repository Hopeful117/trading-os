package com.hope.trading.risk.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {
    public Money {
        amount = Objects.requireNonNull(amount, "amount");
        currency = Objects.requireNonNull(currency, "currency").trim().toUpperCase();
        if (currency.isEmpty()) throw new IllegalArgumentException("currency is required");
    }
    public static Money zero(String currency) { return new Money(BigDecimal.ZERO, currency); }
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }
    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }
    private void requireSameCurrency(Money other) {
        if (!currency.equals(Objects.requireNonNull(other).currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
    }
}
