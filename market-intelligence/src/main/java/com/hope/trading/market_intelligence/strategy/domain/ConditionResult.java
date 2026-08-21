package com.hope.trading.market_intelligence.strategy.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Trace of one deterministic condition evaluation, for auditability and future
 * StrategyMatch provenance.
 */
public record ConditionResult(String conditionId, boolean passed, String observedValue) {

    public ConditionResult {
        Objects.requireNonNull(conditionId, "conditionId is required");
        if (conditionId.isBlank()) {
            throw new IllegalArgumentException("conditionId must not be blank");
        }
        observedValue = observedValue == null ? null : observedValue.trim();
    }

    public static ConditionResult of(String conditionId, boolean passed, BigDecimal observed) {
        return new ConditionResult(conditionId, passed,
                observed == null ? null : observed.toPlainString());
    }
}
