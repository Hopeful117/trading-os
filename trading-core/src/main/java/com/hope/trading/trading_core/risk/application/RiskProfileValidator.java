package com.hope.trading.trading_core.risk.application;

import com.hope.trading.risk.domain.RiskRuleIds;
import com.hope.trading.risk.domain.RiskTypes.PolicyAuthority;
import com.hope.trading.risk.domain.RiskTypes.RuleCategory;
import com.hope.trading.risk.domain.RiskTypes.RuleSeverity;
import com.hope.trading.risk.policy.EffectiveRiskRuleSet;
import com.hope.trading.risk.policy.RuleConfiguration;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class RiskProfileValidator {
    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$");
    private static final Set<String> REQUIRED_RULES = Set.of(
            RiskRuleIds.MAX_POSITION_RISK, RiskRuleIds.MAX_EXPOSURE, RiskRuleIds.DAILY_DRAWDOWN);

    public EffectiveRiskRuleSet validate(RiskPersistence.Profile profile, boolean assigned) {
        if (profile == null || profile.rules() == null || !semanticVersion(profile.semanticVersion())
                || blank(profile.policyId()) || !semanticVersion(profile.policyVersion())
                || blank(profile.provenance()) || (assigned && blank(profile.assignmentProvenance()))
                || !validAuthority(profile.authority())) {
            throw new RiskProfileValidationException("EFFECTIVE_RISK_PROFILE_INVALID");
        }
        Set<String> ruleIds = profile.rules().stream()
                .map(RiskPersistence.ProfileRule::ruleId).collect(java.util.stream.Collectors.toSet());
        if (profile.rules().size() != REQUIRED_RULES.size() || !ruleIds.equals(REQUIRED_RULES)) {
            throw new RiskProfileValidationException("EFFECTIVE_RISK_PROFILE_INCOMPLETE");
        }
        List<RuleConfiguration> rules = profile.rules().stream().map(rule -> {
            if (blank(rule.provenance()) || !semanticVersion(rule.ruleVersion())
                    || rule.maximumRatio() == null || rule.maximumRatio().signum() <= 0
                    || rule.priority() < 0 || !validRuleVocabulary(rule)) {
                throw new RiskProfileValidationException("EFFECTIVE_RISK_PROFILE_INVALID");
            }
            return new RuleConfiguration(rule.ruleId(), rule.ruleVersion(),
                    RuleCategory.valueOf(rule.category()), RuleSeverity.valueOf(rule.severity()),
                    rule.priority(), Map.of("maximumRatio", rule.maximumRatio()));
        }).toList();
        return new EffectiveRiskRuleSet(rules, Map.of(profile.policyId(), profile.policyVersion()));
    }

    private boolean validRuleVocabulary(RiskPersistence.ProfileRule rule) {
        try {
            RuleSeverity.valueOf(rule.severity());
            RuleCategory category = RuleCategory.valueOf(rule.category());
            return category == switch (rule.ruleId()) {
                case RiskRuleIds.MAX_POSITION_RISK -> RuleCategory.POSITION;
                case RiskRuleIds.MAX_EXPOSURE -> RuleCategory.PORTFOLIO;
                case RiskRuleIds.DAILY_DRAWDOWN -> RuleCategory.ACCOUNT;
                default -> null;
            };
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private boolean validAuthority(String authority) {
        try {
            PolicyAuthority.valueOf(authority);
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private boolean semanticVersion(String value) {
        return value != null && SEMVER.matcher(value).matches();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
