package com.hope.trading.market_intelligence.domain.opportunity;

import java.math.*;
import java.util.Objects;

/** Business priority from 0 to 100; never a probability of success. */
public record OpportunityScore(BigDecimal value) implements Comparable<OpportunityScore> {
    public OpportunityScore {
        value = Objects.requireNonNull(value, "value").setScale(2, RoundingMode.HALF_UP);
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Opportunity score must be between 0 and 100");
        }
    }
    @Override public int compareTo(OpportunityScore other) {
        return value.compareTo(other.value);
    }
}
