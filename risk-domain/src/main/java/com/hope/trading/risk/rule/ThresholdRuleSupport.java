package com.hope.trading.risk.rule;

import com.hope.trading.risk.domain.RiskTypes.RuleStatus;
import com.hope.trading.risk.policy.RuleConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

final class ThresholdRuleSupport {
    private ThresholdRuleSupport() {}
    static RiskRuleResult maximum(RuleConfiguration configuration, BigDecimal current,
                                  BigDecimal maximum, Instant time, String code) {
        RuleStatus status = current.compareTo(maximum) <= 0 ? RuleStatus.PASS
                : configuration.severity()
                    == com.hope.trading.risk.domain.RiskTypes.RuleSeverity.WARNING
                        ? RuleStatus.WARNING : RuleStatus.FAILURE;
        Map<String, BigDecimal> values = Map.of(
                "current", current, "maximum", maximum,
                "exceeded", current.subtract(maximum).max(BigDecimal.ZERO));
        return new RiskRuleResult(configuration.ruleId(), configuration.ruleVersion(),
                status, configuration.severity(), new RuleExplanation(code, values),
                values, time, Map.of());
    }
}
