package com.hope.trading.market_intelligence.domain.opportunity;

import java.util.Objects;

/**
 * Trader-oriented projection of one matched strategy condition
 * (Story 0029). {@code condition} is the stable, business-readable
 * condition identifier chosen by the strategy evaluator; {@code
 * observedValue} is the deterministic measurement string recorded at
 * match time. Only conditions that passed are projected: a persisted
 * StrategyMatch exists only for MATCH evaluations.
 */
public record OpportunityTrigger(String condition, String observedValue) {
    public OpportunityTrigger {
        condition = required(condition, "condition");
        if (observedValue != null) {
            observedValue = observedValue.trim();
            if (observedValue.isEmpty()) {
                observedValue = null;
            }
        }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return result;
    }
}
