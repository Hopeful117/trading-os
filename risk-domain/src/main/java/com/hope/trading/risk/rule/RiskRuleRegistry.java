package com.hope.trading.risk.rule;

import java.util.*;

public final class RiskRuleRegistry {
    private final Map<String, RiskRule> rules;
    public RiskRuleRegistry(Collection<RiskRule> rules) {
        Map<String, RiskRule> indexed = new HashMap<>();
        for (RiskRule rule : rules) {
            if (indexed.put(rule.id(), rule) != null) {
                throw new IllegalArgumentException("Duplicate rule: " + rule.id());
            }
        }
        this.rules = Map.copyOf(indexed);
    }
    public Optional<RiskRule> find(String id) { return Optional.ofNullable(rules.get(id)); }
}
