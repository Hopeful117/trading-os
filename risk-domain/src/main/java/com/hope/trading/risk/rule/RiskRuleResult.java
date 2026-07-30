package com.hope.trading.risk.rule;

import com.hope.trading.risk.domain.RiskTypes.RuleSeverity;
import com.hope.trading.risk.domain.RiskTypes.RuleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record RiskRuleResult(
        String ruleId, String ruleVersion, RuleStatus status, RuleSeverity severity,
        RuleExplanation explanation, Map<String, BigDecimal> metrics,
        Instant evaluatedAt, Map<String, String> metadata
) {
    public RiskRuleResult {
        Objects.requireNonNull(ruleId); Objects.requireNonNull(ruleVersion);
        Objects.requireNonNull(status); Objects.requireNonNull(severity);
        Objects.requireNonNull(explanation); Objects.requireNonNull(evaluatedAt);
        metrics = Map.copyOf(metrics); metadata = Map.copyOf(metadata);
    }
}
