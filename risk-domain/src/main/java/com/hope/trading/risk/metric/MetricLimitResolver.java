package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Ratio;
import com.hope.trading.risk.policy.EffectiveRiskRuleSet;
import com.hope.trading.risk.domain.RiskRuleIds;
import java.math.BigDecimal;

/** Resolves only inputs needed by derived metrics; it never evaluates a rule. */
public final class MetricLimitResolver {
    public Ratio maximumPositionRisk(EffectiveRiskRuleSet ruleSet) {
        BigDecimal value = ruleSet.rules().stream()
                .filter(r -> r.ruleId().equals(RiskRuleIds.MAX_POSITION_RISK))
                .findFirst().map(r -> r.requiredParameter("maximumRatio"))
                .orElse(BigDecimal.ONE);
        return new Ratio(value);
    }
}
