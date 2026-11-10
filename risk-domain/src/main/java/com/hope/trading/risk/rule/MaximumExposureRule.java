package com.hope.trading.risk.rule;

import com.hope.trading.risk.metric.RiskRuleEvaluationContext;
import com.hope.trading.risk.policy.RuleConfiguration;
import com.hope.trading.risk.domain.RiskRuleIds;
import java.time.Instant;

/**
 * Limits projected aggregate notional allocation relative to observed equity.
 * This is not a stop-loss, margin-utilization, or leverage calculation.
 */
public final class MaximumExposureRule implements RiskRule {
    public static final String ID = RiskRuleIds.MAX_EXPOSURE;
    @Override public String id() { return ID; }
    @Override public boolean evidenceAvailable(RiskRuleEvaluationContext context) {
        return context.metrics().exposureRatio() != null;
    }
    @Override public String incompleteReason() { return "EQUITY_REQUIRED_FOR_EXPOSURE"; }
    @Override public RiskRuleResult evaluate(RiskRuleEvaluationContext context,
            RuleConfiguration configuration, Instant time) {
        return ThresholdRuleSupport.maximum(configuration,
                context.metrics().exposureRatio().value(),
                configuration.requiredParameter("maximumRatio"), time,
                "maximum-exposure");
    }
}
