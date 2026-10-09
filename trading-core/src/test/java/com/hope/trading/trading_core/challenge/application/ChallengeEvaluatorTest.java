package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.risk.audit.ContextMetadata;
import com.hope.trading.risk.audit.TraceMetadata;
import com.hope.trading.risk.domain.RiskTypes;
import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.risk.rule.RuleExplanation;
import com.hope.trading.risk.rule.RiskRuleResult;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private final UUID policyId = UUID.randomUUID();
    private final ChallengeDefinition definition = ChallengeDefinition.create(UUID.randomUUID(), "1.0.0",
            "TEST_PROVIDER", "TEST_PRODUCT", "PLAN_A", "USD", new BigDecimal("10000"),
            new BigDecimal("0.10"), ProgressionValueSource.BALANCE, policyId, "1.0.0",
            "https://example.test", NOW, NOW, "test", NOW);
    private final ChallengeEvaluator evaluator = new ChallengeEvaluator();

    @Test
    void leavesActiveWhenTargetIsNotMet() {
        ChallengeInstance challenge = challenge();
        assertThat(evaluator.evaluate(challenge, definition, account("10500"), risk(List.of()), UUID.randomUUID(), NOW))
                .isEqualTo(ChallengeStatus.ACTIVE);
        assertThat(challenge.status()).isEqualTo(ChallengeStatus.ACTIVE);
    }

    @Test
    void passesAtInclusiveBalanceTarget() {
        ChallengeInstance challenge = challenge();
        assertThat(evaluator.evaluate(challenge, definition, account("11000"), risk(List.of()), UUID.randomUUID(), NOW))
                .isEqualTo(ChallengeStatus.PASSED);
        assertThat(challenge.terminalReason()).isEqualTo("Challenge balance target reached");
    }

    @Test
    void blockingChallengeRiskWinsWhenTargetAlsoMet() {
        ChallengeInstance challenge = challenge();
        RiskRuleResult violation = rule("DAILY_DRAWDOWN", RiskTypes.RuleStatus.FAILURE,
                RiskTypes.RuleSeverity.BLOCKING);
        assertThat(evaluator.evaluate(challenge, definition, account("11000"), risk(List.of(violation)),
                UUID.randomUUID(), NOW)).isEqualTo(ChallengeStatus.BREACHED);
        assertThat(challenge.terminalReason()).isEqualTo("Blocking Challenge Risk constraint breached");
    }

    @Test
    void warningDoesNotBreachChallenge() {
        ChallengeInstance challenge = challenge();
        RiskRuleResult warning = rule("DAILY_DRAWDOWN", RiskTypes.RuleStatus.WARNING,
                RiskTypes.RuleSeverity.WARNING);
        assertThat(evaluator.evaluate(challenge, definition, account("10500"), risk(List.of(warning)),
                UUID.randomUUID(), NOW)).isEqualTo(ChallengeStatus.ACTIVE);
    }

    @Test
    void sameVersionFromDifferentPolicyDoesNotBreachChallenge() {
        ChallengeInstance challenge = challenge();
        RiskValidationResult result = risk(List.of(rule("DAILY_DRAWDOWN", RiskTypes.RuleStatus.FAILURE,
                RiskTypes.RuleSeverity.BLOCKING)));
        org.mockito.Mockito.when(result.trace()).thenReturn(new TraceMetadata(UUID.randomUUID(), UUID.randomUUID(), "test",
                Map.of(UUID.randomUUID().toString(), "1.0.0"), Map.of(), new ContextMetadata(
                UUID.randomUUID(), 1, UUID.randomUUID(), 1, 1, 1, NOW, NOW, NOW, NOW)));

        assertThat(evaluator.evaluate(challenge, definition, account("10500"), result, UUID.randomUUID(), NOW))
                .isEqualTo(ChallengeStatus.ACTIVE);
    }

    @Test
    void terminalEvaluationIsIdempotent() {
        ChallengeInstance challenge = challenge();
        evaluator.evaluate(challenge, definition, account("11000"), risk(List.of()), UUID.randomUUID(), NOW);
        assertThat(evaluator.evaluate(challenge, definition, account("9000"), risk(List.of()), UUID.randomUUID(),
                NOW.plusSeconds(1))).isEqualTo(ChallengeStatus.PASSED);
        assertThat(challenge.passedAt()).isEqualTo(NOW);
    }

    private ChallengeInstance challenge() {
        return ChallengeInstance.start(UUID.randomUUID(), UUID.randomUUID(), definition,
                definition.startingCapital(), NOW);
    }

    private Account account(String balance) {
        User user = User.builder().userId(UUID.randomUUID()).username("owner").role(Role.ROLE_USER).build();
        Account account = Account.builder().accountId(UUID.randomUUID()).baseCurrency("USD")
                .startingBalance(new BigDecimal("10000")).equity(new BigDecimal(balance))
                .peakEquity(new BigDecimal(balance)).user(user).build();
        account.addBalance(AccountBalance.builder().asset("USD").amount(new BigDecimal(balance)).build());
        return account;
    }

    private RiskValidationResult risk(List<RiskRuleResult> rules) {
        RiskValidationResult result = org.mockito.Mockito.mock(RiskValidationResult.class);
        TraceMetadata trace = new TraceMetadata(UUID.randomUUID(), UUID.randomUUID(), "test",
                Map.of(policyId.toString(), "1.0.0"), Map.of(), new ContextMetadata(
                UUID.randomUUID(), 1, UUID.randomUUID(), 1, 1, 1, NOW, NOW, NOW, NOW));
        org.mockito.Mockito.when(result.evaluationStatus()).thenReturn(RiskTypes.EvaluationStatus.COMPLETED);
        org.mockito.Mockito.when(result.ruleResults()).thenReturn(rules);
        org.mockito.Mockito.when(result.trace()).thenReturn(trace);
        return result;
    }

    private RiskRuleResult rule(String id, RiskTypes.RuleStatus status, RiskTypes.RuleSeverity severity) {
        return new RiskRuleResult(id, "1.0.0", status, severity,
                new RuleExplanation("test", Map.of()), Map.of(), NOW, Map.of());
    }
}
