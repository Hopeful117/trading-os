package com.hope.trading.risk.rule;

import com.hope.trading.risk.metric.RiskRuleEvaluationContext;
import com.hope.trading.risk.policy.RuleConfiguration;
import java.time.Instant;

public interface RiskRule {
    String id();
    default boolean supports(RiskRuleEvaluationContext context) { return true; }
    RiskRuleResult evaluate(RiskRuleEvaluationContext context, RuleConfiguration configuration,
                            Instant evaluationTime);
}
