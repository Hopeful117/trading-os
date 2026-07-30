package com.hope.trading.risk.rule;

import com.hope.trading.risk.metric.RiskRuleEvaluationContext;
import com.hope.trading.risk.policy.RuleConfiguration;
import com.hope.trading.risk.domain.RiskRuleIds;
import java.time.Instant;

public final class MaximumPositionRiskRule implements RiskRule {
    public static final String ID = RiskRuleIds.MAX_POSITION_RISK;
    @Override public String id() { return ID; }
    @Override public boolean supports(RiskRuleEvaluationContext context) {
        return context.proposedTradePresent();
    }
    @Override public RiskRuleResult evaluate(RiskRuleEvaluationContext context,
            RuleConfiguration configuration, Instant time) {
        return ThresholdRuleSupport.maximum(configuration,
                context.metrics().positionRiskRatio().value(),
                configuration.requiredParameter("maximumRatio"), time,
                "maximum-position-risk");
    }
}
