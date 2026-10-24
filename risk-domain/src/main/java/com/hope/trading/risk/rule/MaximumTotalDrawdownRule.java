package com.hope.trading.risk.rule;

import com.hope.trading.risk.domain.RiskRuleIds;
import com.hope.trading.risk.metric.RiskRuleEvaluationContext;
import com.hope.trading.risk.policy.RuleConfiguration;
import java.time.Instant;

/** Evaluates a static drawdown floor relative to the account starting balance. */
public final class MaximumTotalDrawdownRule implements RiskRule {
    public static final String RULE_ID = RiskRuleIds.MAX_TOTAL_DRAWDOWN;

    @Override public String id() { return RULE_ID; }

    @Override public RiskRuleResult evaluate(RiskRuleEvaluationContext context,
                                              RuleConfiguration configuration, Instant time) {
        Object boundary = configuration.parameters().getOrDefault("breachBoundary", "INCLUSIVE");
        if (!(boundary instanceof String value)
                || (!"INCLUSIVE".equals(value) && !"STRICT".equals(value))) {
            throw new IllegalArgumentException("Unsupported total drawdown breach boundary");
        }
        var current = context.metrics().totalDrawdownRatio().orElseThrow(
                () -> new IllegalStateException("Account starting balance unavailable")).value();
        var maximum = configuration.requiredParameter("maximumRatio");
        if ("STRICT".equals(value)) {
            return ThresholdRuleSupport.maximum(configuration, current, maximum, time,
                    "maximum-total-drawdown");
        }
        return ThresholdRuleSupport.maximumInclusiveBreach(configuration, current, maximum, time,
                "maximum-total-drawdown");
    }
}
