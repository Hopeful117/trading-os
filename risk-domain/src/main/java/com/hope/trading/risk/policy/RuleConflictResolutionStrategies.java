package com.hope.trading.risk.policy;

import java.util.Map;
import java.util.Objects;
import com.hope.trading.risk.domain.RiskRuleIds;

public final class RuleConflictResolutionStrategies {
    private final Map<String, RuleConflictResolutionStrategy> strategies;
    public RuleConflictResolutionStrategies(
            Map<String, RuleConflictResolutionStrategy> strategies) {
        this.strategies = Map.copyOf(strategies);
    }
    public RuleConflictResolutionStrategy requiredFor(String ruleId) {
        RuleConflictResolutionStrategy strategy = strategies.get(ruleId);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "No conflict resolution strategy for rule: " + ruleId);
        }
        return strategy;
    }
    public static RuleConflictResolutionStrategies standard() {
        var upperBound = new UpperBoundRuleResolutionStrategy();
        return new RuleConflictResolutionStrategies(Map.of(
                RiskRuleIds.MAX_POSITION_RISK, upperBound,
                RiskRuleIds.MAX_EXPOSURE, upperBound,
                RiskRuleIds.DAILY_DRAWDOWN, upperBound));
    }
}
