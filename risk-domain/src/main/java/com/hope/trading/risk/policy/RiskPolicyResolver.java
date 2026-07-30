package com.hope.trading.risk.policy;

import java.util.*;

/** Resolves hierarchy while delegating parameter semantics to rule strategies. */
public final class RiskPolicyResolver {
    private final RuleConflictResolutionStrategies strategies;
    public RiskPolicyResolver() {
        this(RuleConflictResolutionStrategies.standard());
    }
    public RiskPolicyResolver(RuleConflictResolutionStrategies strategies) {
        this.strategies = Objects.requireNonNull(strategies);
    }
    public EffectiveRiskRuleSet resolve(List<RiskPolicy> policies) {
        List<RiskPolicy> ordered = policies.stream()
                .sorted(Comparator.comparingInt(p -> p.authority().rank())).toList();
        Map<String, RuleConfiguration> resolved = new LinkedHashMap<>();
        Map<String, String> versions = new LinkedHashMap<>();
        for (RiskPolicy policy : ordered) {
            versions.put(policy.policyId(), policy.version());
            for (RuleConfiguration candidate : policy.rules()) {
                resolved.merge(candidate.ruleId(), candidate,
                        (higher, lower) -> strategies.requiredFor(candidate.ruleId())
                                .resolve(higher, lower));
            }
        }
        return new EffectiveRiskRuleSet(
                List.copyOf(resolved.values()), versions);
    }
}
