package com.hope.trading.risk.policy;

import java.math.BigDecimal;
import java.util.*;

/** Resolution for rules whose numeric parameters are strict upper bounds. */
public final class UpperBoundRuleResolutionStrategy
        implements RuleConflictResolutionStrategy {
    @Override public RuleConfiguration resolve(
            RuleConfiguration higher, RuleConfiguration lower) {
        if (higher.category() != lower.category()) {
            throw new IllegalArgumentException(
                    "Conflicting category for " + higher.ruleId());
        }
        Map<String, Object> parameters =
                new LinkedHashMap<>(higher.parameters());
        lower.parameters().forEach((key, value) -> parameters.merge(
                key, value, this::minimumDecimal));
        return new RuleConfiguration(higher.ruleId(), higher.ruleVersion(),
                higher.category(), strongest(higher, lower),
                Math.min(higher.priority(), lower.priority()), parameters);
    }

    private Object minimumDecimal(Object higher, Object lower) {
        if (!(higher instanceof BigDecimal higherDecimal)
                || !(lower instanceof BigDecimal lowerDecimal)) {
            throw new IllegalArgumentException(
                    "Upper-bound strategy requires decimal parameters");
        }
        return higherDecimal.min(lowerDecimal);
    }

    private com.hope.trading.risk.domain.RiskTypes.RuleSeverity strongest(
            RuleConfiguration higher, RuleConfiguration lower) {
        return severityRank(lower.severity()) > severityRank(higher.severity())
                ? lower.severity() : higher.severity();
    }

    private int severityRank(
            com.hope.trading.risk.domain.RiskTypes.RuleSeverity severity) {
        return switch (severity) {
            case INFO -> 0;
            case WARNING -> 1;
            case BLOCKING -> 2;
        };
    }
}
