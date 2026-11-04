package com.hope.trading.trading_core.risk.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.risk.domain.RiskTypes.RuleCategory;
import com.hope.trading.risk.domain.RiskTypes.RuleSeverity;
import com.hope.trading.risk.policy.EffectiveRiskRuleSet;
import com.hope.trading.risk.policy.RuleConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskFactRequirementsTest {
    @Test
    void mapsOnlyFactsConsumedByTheSelectedRules() {
        EffectiveRiskRuleSet rules = new EffectiveRiskRuleSet(List.of(
                rule("MAX_POSITION_RISK", RuleCategory.POSITION),
                rule("MAX_EXPOSURE", RuleCategory.PORTFOLIO),
                rule("DAILY_DRAWDOWN", RuleCategory.ACCOUNT)), Map.of());

        RiskFactRequirements requirements = RiskFactRequirements.from(rules);

        assertThat(requirements.balance()).isTrue();
        assertThat(requirements.equity()).isTrue();
        assertThat(requirements.positions()).isTrue();
        assertThat(requirements.startingBalance()).isFalse();
        assertThat(requirements.usedMargin()).isFalse();
        assertThat(requirements.freeMargin()).isFalse();
        assertThat(requirements.closedDeals()).isFalse();
    }

    @Test
    void totalDrawdownAddsStartingBalanceWithoutAddingMarginDependencies() {
        RiskFactRequirements requirements = RiskFactRequirements.from(new EffectiveRiskRuleSet(List.of(
                rule("MAX_TOTAL_DRAWDOWN", RuleCategory.ACCOUNT)), Map.of()));

        assertThat(requirements.equity()).isTrue();
        assertThat(requirements.startingBalance()).isTrue();
        assertThat(requirements.usedMargin()).isFalse();
        assertThat(requirements.freeMargin()).isFalse();
    }

    private static RuleConfiguration rule(String id, RuleCategory category) {
        return new RuleConfiguration(id, "1.0.0", category, RuleSeverity.BLOCKING, 1,
                Map.of("maximumRatio", new BigDecimal("0.03")));
    }
}
