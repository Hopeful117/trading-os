package com.hope.trading.risk.rule;

import com.hope.trading.risk.domain.RiskRuleIds;
import com.hope.trading.risk.metric.RiskRuleEvaluationContext;
import com.hope.trading.risk.policy.RuleConfiguration;
import java.time.Instant;

/** Evaluates a static drawdown floor relative to the account starting balance. */
public final class MaximumTotalDrawdownRule implements RiskRule {
    public static final String ID = RiskRuleIds.MAX_TOTAL_DRAWDOWN;

    @Override public String id() { return ID; }

    @Override public RiskRuleResult evaluate(RiskRuleEvaluationContext context,
                                              RuleConfiguration configuration, Instant time) {
        return ThresholdRuleSupport.maximumInclusiveBreach(configuration,
                context.metrics().totalDrawdownRatio().orElseThrow(
                        () -> new IllegalStateException("Account starting balance unavailable")).value(),
                configuration.requiredParameter("maximumRatio"), time,
                "maximum-total-drawdown");
    }
}
