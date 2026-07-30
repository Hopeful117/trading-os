package com.hope.trading.risk.engine;

import com.hope.trading.risk.domain.RiskTypes.*;
import com.hope.trading.risk.rule.RiskRuleResult;
import java.util.List;
import java.util.Optional;

/** Applies decision semantics after complete rule traversal. */
public final class RiskResultAggregator {
    public Aggregation aggregate(List<RiskRuleResult> results,
                                 boolean incomplete, boolean failed) {
        List<RiskRuleResult> violations = results.stream()
                .filter(r -> r.status() == RuleStatus.FAILURE).toList();
        List<RiskRuleResult> warnings = results.stream()
                .filter(r -> r.status() == RuleStatus.WARNING).toList();
        EvaluationStatus status = failed ? EvaluationStatus.FAILED
                : incomplete ? EvaluationStatus.INCOMPLETE : EvaluationStatus.COMPLETED;
        boolean blockingFailure = violations.stream()
                .anyMatch(r -> r.severity() == RuleSeverity.BLOCKING);
        Optional<RiskDecision> decision = status != EvaluationStatus.COMPLETED
                ? Optional.empty()
                : Optional.of(blockingFailure ? RiskDecision.REJECTED
                    : !warnings.isEmpty() ? RiskDecision.APPROVED_WITH_WARNINGS
                    : RiskDecision.APPROVED);
        return new Aggregation(status, decision, violations, warnings);
    }

    public record Aggregation(
            EvaluationStatus status, Optional<RiskDecision> decision,
            List<RiskRuleResult> violations, List<RiskRuleResult> warnings
    ) {
        public Aggregation {
            violations = List.copyOf(violations);
            warnings = List.copyOf(warnings);
        }
    }
}
