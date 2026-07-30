package com.hope.trading.risk.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Decimal ratio: 0.01 means one percent. */
public record Ratio(BigDecimal value) implements Comparable<Ratio> {
    public Ratio {
        value = Objects.requireNonNull(value, "value");
        if (value.signum() < 0) throw new IllegalArgumentException("ratio cannot be negative");
    }
    public static Ratio zero() { return new Ratio(BigDecimal.ZERO); }
    @Override public int compareTo(Ratio other) { return value.compareTo(other.value); }
}
