package com.hope.trading.risk;

import com.hope.trading.risk.policy.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static com.hope.trading.risk.RiskFixture.*;
import static com.hope.trading.risk.domain.RiskTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class RiskPolicyResolverTest {
    @Test void lowerAuthorityMayTightenButNeverWeakenPlatformConstraint() {
        var platform = new RiskPolicy("platform", "1", PolicyAuthority.PLATFORM,
                List.of(rule("MAX_EXPOSURE", RuleCategory.PORTFOLIO,
                        RuleSeverity.BLOCKING, "0.50")));
        var userWeakening = new RiskPolicy("user", "4", PolicyAuthority.USER,
                List.of(rule("MAX_EXPOSURE", RuleCategory.PORTFOLIO,
                        RuleSeverity.WARNING, "0.80")));
        var resolved = new RiskPolicyResolver().resolve(List.of(userWeakening, platform));
        assertEquals(0, resolved.rules().getFirst().requiredParameter("maximumRatio")
                .compareTo(new java.math.BigDecimal("0.50")));
        assertEquals(RuleSeverity.BLOCKING, resolved.rules().getFirst().severity());
        assertEquals("4", resolved.policyVersions().get("user"));
    }

    @Test void lowerAuthorityCanTightenThreshold() {
        var platform = new RiskPolicy("platform", "1", PolicyAuthority.PLATFORM,
                List.of(rule("MAX_EXPOSURE", RuleCategory.PORTFOLIO,
                        RuleSeverity.BLOCKING, "0.50")));
        var account = new RiskPolicy("account", "2", PolicyAuthority.ACCOUNT,
                List.of(rule("MAX_EXPOSURE", RuleCategory.PORTFOLIO,
                        RuleSeverity.BLOCKING, "0.30")));
        assertEquals(0, new RiskPolicyResolver().resolve(List.of(account, platform))
                .rules().getFirst().requiredParameter("maximumRatio")
                .compareTo(new java.math.BigDecimal("0.30")));
    }

    @Test void customParameterSemanticsAreAddedWithoutChangingResolver() {
        RuleConflictResolutionStrategy minimumRequired =
                (higher, lower) -> new RuleConfiguration(
                        higher.ruleId(), higher.ruleVersion(), higher.category(),
                        higher.severity(), higher.priority(),
                        Map.of("minimumRatio",
                            higher.requiredParameter("minimumRatio").max(
                                lower.requiredParameter("minimumRatio")),
                            "mode", lower.parameters().get("mode")));
        var strategies = new RuleConflictResolutionStrategies(
                Map.of("MINIMUM_BUFFER", minimumRequired));
        var platformRule = new RuleConfiguration("MINIMUM_BUFFER", "1",
                RuleCategory.ACCOUNT, RuleSeverity.BLOCKING, 1,
                Map.of("minimumRatio", new java.math.BigDecimal("0.10"),
                        "mode", "STANDARD"));
        var userRule = new RuleConfiguration("MINIMUM_BUFFER", "1",
                RuleCategory.ACCOUNT, RuleSeverity.BLOCKING, 1,
                Map.of("minimumRatio", new java.math.BigDecimal("0.20"),
                        "mode", "STRICT"));
        var resolved = new RiskPolicyResolver(strategies).resolve(List.of(
                new RiskPolicy("platform", "1", PolicyAuthority.PLATFORM,
                        List.of(platformRule)),
                new RiskPolicy("user", "1", PolicyAuthority.USER,
                        List.of(userRule))));
        assertEquals(0, new java.math.BigDecimal("0.20").compareTo(
                resolved.rules().getFirst().requiredParameter("minimumRatio")));
        assertEquals("STRICT",
                resolved.rules().getFirst().parameters().get("mode"));
    }
}
