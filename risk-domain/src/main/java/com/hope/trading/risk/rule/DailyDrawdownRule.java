package com.hope.trading.risk.rule;

import com.hope.trading.risk.metric.RiskRuleEvaluationContext;
import com.hope.trading.risk.policy.RuleConfiguration;
import com.hope.trading.risk.domain.RiskRuleIds;
import java.time.Instant;

public final class DailyDrawdownRule implements RiskRule {
    public static final String ID = RiskRuleIds.DAILY_DRAWDOWN;
    @Override public String id() { return ID; }
    @Override public RiskRuleResult evaluate(RiskRuleEvaluationContext context,
            RuleConfiguration configuration, Instant time) {
        return ThresholdRuleSupport.maximum(configuration,
                context.metrics().dailyDrawdownRatio().value(),
                configuration.requiredParameter("maximumRatio"), time,
                "daily-drawdown");
    }
}
