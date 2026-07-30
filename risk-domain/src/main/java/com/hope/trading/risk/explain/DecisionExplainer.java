package com.hope.trading.risk.explain;

import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.risk.domain.RiskTypes.RuleStatus;

public final class DecisionExplainer {
    public DecisionExplanation explain(RiskValidationResult result) {
        var decision = result.decision().orElseThrow(
                () -> new IllegalStateException("Incomplete evaluation has no business explanation"));
        var reasons = result.ruleResults().stream()
                .filter(r -> r.status() == RuleStatus.FAILURE || r.status() == RuleStatus.WARNING)
                .map(r -> r.explanation()).toList();
        if (reasons.isEmpty()) {
            reasons = result.ruleResults().stream()
                    .map(r -> r.explanation()).toList();
        }
        return new DecisionExplanation(decision, reasons);
    }
}
