package com.hope.trading.risk.audit;

import com.hope.trading.risk.context.RiskEvaluationContext;
import com.hope.trading.risk.domain.RiskValidationResult;
import java.util.Objects;

/** Complete immutable artifact needed for deterministic replay. */
public record RiskEvaluationRecord(RiskEvaluationContext context, RiskValidationResult result) {
    public RiskEvaluationRecord {
        Objects.requireNonNull(context); Objects.requireNonNull(result);
    }
}
