package com.hope.trading.risk.rule;

import com.hope.trading.risk.metric.RiskRuleEvaluationContext;

@FunctionalInterface
public interface RuleApplicability {
    boolean supports(RiskRuleEvaluationContext context);
}
