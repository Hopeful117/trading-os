package com.hope.trading.market_intelligence.application.tradeplan;

import java.util.*;

public final class PlanningPolicyRegistry {
    private final List<PlanningPolicy> policies;
    public PlanningPolicyRegistry(Collection<PlanningPolicy> policies) {
        Map<String, PlanningPolicy> unique = new HashMap<>();
        for (PlanningPolicy policy : policies) {
            if (unique.putIfAbsent(policy.id(), policy) != null) {
                throw new IllegalArgumentException("Duplicate planning policy: " + policy.id());
            }
        }
        this.policies = unique.values().stream()
                .sorted(Comparator.comparingInt(PlanningPolicy::order)
                        .thenComparing(PlanningPolicy::id)).toList();
    }
    public List<PlanningPolicy> applicable(PlanningInput input) {
        return policies.stream().filter(policy -> policy.supports(input)).toList();
    }
    public List<String> activePolicyIds() {
        return policies.stream().map(PlanningPolicy::id).toList();
    }
}
