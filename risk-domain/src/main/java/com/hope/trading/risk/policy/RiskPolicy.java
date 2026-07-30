package com.hope.trading.risk.policy;

import com.hope.trading.risk.domain.RiskTypes.PolicyAuthority;
import java.util.List;
import java.util.Objects;

public record RiskPolicy(String policyId, String version, PolicyAuthority authority,
                         List<RuleConfiguration> rules) {
    public RiskPolicy {
        policyId = Objects.requireNonNull(policyId).trim();
        version = Objects.requireNonNull(version).trim();
        Objects.requireNonNull(authority);
        rules = List.copyOf(rules);
        if (policyId.isEmpty() || version.isEmpty()) throw new IllegalArgumentException("Policy identity required");
    }
}
