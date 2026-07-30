package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.RiskTypes.ValidationMode;
import java.util.Objects;

/** Immutable input given to rules after every financial calculation is complete. */
public record RiskRuleEvaluationContext(
        ValidationMode mode, boolean proposedTradePresent, RiskMetrics metrics
) {
    public RiskRuleEvaluationContext {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(metrics);
    }
}
