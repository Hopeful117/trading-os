package com.hope.trading.trading_core.risk.application;

import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskProfileValidatorTest {
    private final RiskProfileValidator validator = new RiskProfileValidator();

    @Test
    void totalDrawdownIsOptionalButUsesTheGenericAccountVocabulary() {
        var profile = profile(List.of(
                rule("MAX_POSITION_RISK", "POSITION", "0.01"),
                rule("MAX_EXPOSURE", "PORTFOLIO", "0.03"),
                rule("DAILY_DRAWDOWN", "ACCOUNT", "0.03"),
                rule("MAX_TOTAL_DRAWDOWN", "ACCOUNT", "0.05")));

        var effective = validator.validate(profile, false);

        assertThat(effective.rules()).extracting(rule -> rule.ruleId())
                .contains("MAX_TOTAL_DRAWDOWN");
    }

    @Test
    void duplicateRulesRemainInvalid() {
        var duplicate = profile(List.of(
                rule("MAX_POSITION_RISK", "POSITION", "0.01"),
                rule("MAX_POSITION_RISK", "POSITION", "0.02"),
                rule("MAX_EXPOSURE", "PORTFOLIO", "0.03"),
                rule("DAILY_DRAWDOWN", "ACCOUNT", "0.03")));

        assertThatThrownBy(() -> validator.validate(duplicate, false))
                .isInstanceOf(RiskProfileValidationException.class)
                .hasMessage("EFFECTIVE_RISK_PROFILE_INCOMPLETE");
    }

    @Test
    void strictBoundaryIsAcceptedForDrawdownRules() {
        var effective = validator.validate(profile(List.of(
                rule("MAX_POSITION_RISK", "POSITION", "0.01"),
                rule("MAX_EXPOSURE", "PORTFOLIO", "0.03"),
                rule("DAILY_DRAWDOWN", "ACCOUNT", "0.03", "STRICT"),
                rule("MAX_TOTAL_DRAWDOWN", "ACCOUNT", "0.10", "STRICT"))), false);

        assertThat(effective.rules()).filteredOn(rule -> "DAILY_DRAWDOWN".equals(rule.ruleId()))
                .singleElement().satisfies(rule -> assertThat(rule.parameters().get("breachBoundary")).isEqualTo("STRICT"));
        assertThat(effective.rules()).filteredOn(rule -> "MAX_TOTAL_DRAWDOWN".equals(rule.ruleId()))
                .singleElement().satisfies(rule -> assertThat(rule.parameters().get("breachBoundary")).isEqualTo("STRICT"));
        assertThatThrownBy(() -> validator.validate(profile(List.of(
                rule("MAX_POSITION_RISK", "POSITION", "0.01", "STRICT"),
                rule("MAX_EXPOSURE", "PORTFOLIO", "0.03"),
                rule("DAILY_DRAWDOWN", "ACCOUNT", "0.03"))), false))
                .isInstanceOf(RiskProfileValidationException.class)
                .hasMessage("EFFECTIVE_RISK_PROFILE_INVALID");
    }

    @Test
    void invalidBoundaryIsRejected() {
        assertThatThrownBy(() -> validator.validate(profile(List.of(
                rule("MAX_POSITION_RISK", "POSITION", "0.01"),
                rule("MAX_EXPOSURE", "PORTFOLIO", "0.03"),
                rule("DAILY_DRAWDOWN", "ACCOUNT", "0.03", "INVALID"))), false))
                .isInstanceOf(RiskProfileValidationException.class)
                .hasMessage("EFFECTIVE_RISK_PROFILE_INVALID");
    }

    private RiskPersistence.Profile profile(List<RiskPersistence.ProfileRule> rules) {
        return new RiskPersistence.Profile(UUID.randomUUID(), "1.0.0", "policy", "1.0.0",
                "PLATFORM", Instant.EPOCH, "profile-source", null, null, rules);
    }

    private RiskPersistence.ProfileRule rule(String id, String category, String maximum) {
        return new RiskPersistence.ProfileRule(id, "1.0.0", category, "BLOCKING", 10,
                new BigDecimal(maximum), "rule-source:" + id);
    }

    private RiskPersistence.ProfileRule rule(String id, String category, String maximum, String boundary) {
        return new RiskPersistence.ProfileRule(id, "1.0.0", category, "BLOCKING", 10,
                new BigDecimal(maximum), "rule-source:" + id, boundary);
    }
}
