package com.hope.trading.risk.rule;

import com.hope.trading.risk.metric.RiskRuleEvaluationContext;
import com.hope.trading.risk.policy.RuleConfiguration;
import com.hope.trading.risk.domain.RiskRuleIds;
import java.time.Instant;

public final class MaximumExposureRule implements RiskRule {
    public static final String ID = RiskRuleIds.MAX_EXPOSURE;
    @Override public String id() { return ID; }
    @Override public RiskRuleResult evaluate(RiskRuleEvaluationContext context,
            RuleConfiguration configuration, Instant time) {
        return ThresholdRuleSupport.maximum(configuration,
                context.metrics().exposureRatio().value(),
                configuration.requiredParameter("maximumRatio"), time,
                "maximum-exposure");
    }
}
