package com.hope.trading.risk.policy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record EffectiveRiskRuleSet(List<RuleConfiguration> rules,
                                   Map<String, String> policyVersions) {
    public EffectiveRiskRuleSet {
        rules = rules.stream().sorted(Comparator
                .comparing((RuleConfiguration r) -> r.category().ordinal())
                .thenComparingInt(RuleConfiguration::priority)
                .thenComparing(RuleConfiguration::ruleId)).toList();
        policyVersions = Map.copyOf(policyVersions);
    }
}
