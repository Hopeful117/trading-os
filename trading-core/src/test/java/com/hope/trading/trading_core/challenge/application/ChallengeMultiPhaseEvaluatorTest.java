package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.risk.audit.ContextMetadata;
import com.hope.trading.risk.audit.TraceMetadata;
import com.hope.trading.risk.domain.RiskTypes;
import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.risk.rule.RiskRuleResult;
import com.hope.trading.risk.rule.RuleExplanation;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengePhaseDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengePhaseProgressionState;
import com.hope.trading.trading_core.challenge.domain.ChallengeProgressionEvidence;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.challenge.domain.ExecutedTradingEvidence;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChallengeMultiPhaseEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");
    private final UUID policyId = UUID.randomUUID();
    private final ChallengeEvaluator evaluator = new ChallengeEvaluator();

    @Test
    void completesOrderedPhasesWithoutSkippingAndRequiresProviderConfirmationWhenDeclared() {
        ChallengeDefinition definition = definition(List.of(
                ChallengePhaseDefinition.create("FIRST", 1, new BigDecimal("0.10"),
                        ProgressionValueSource.BALANCE, 2, policyId, "1.0.0", false),
                ChallengePhaseDefinition.create("SECOND", 2, new BigDecimal("0.05"),
                        ProgressionValueSource.BALANCE, 1, policyId, "1.0.0", true)));
        ChallengeInstance challenge = ChallengeInstance.start(UUID.randomUUID(), UUID.randomUUID(), definition,
                definition.startingCapital(), NOW);

        ChallengeProgressionEvidence firstEvidence = evidence(Set.of(
                LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23)), false, "");
        assertThat(evaluator.evaluate(challenge, definition, account("11000"), risk(List.of()),
                UUID.randomUUID(), NOW, firstEvidence)).isEqualTo(ChallengeStatus.ACTIVE);
        assertThat(challenge.currentPhaseKey()).isEqualTo("SECOND");
        assertThat(challenge.phaseProgressionState()).isEqualTo(ChallengePhaseProgressionState.ACTIVE);

        ChallengeProgressionEvidence secondEvidence = evidence(Set.of(LocalDate.of(2026, 10, 24)), false, "");
        assertThat(evaluator.evaluate(challenge, definition, account("11500"), risk(List.of()),
                UUID.randomUUID(), NOW, secondEvidence)).isEqualTo(ChallengeStatus.ACTIVE);
        assertThat(challenge.phaseProgressionState()).isEqualTo(ChallengePhaseProgressionState.LOCALLY_SATISFIED);

        ChallengeProgressionEvidence confirmed = evidence(Set.of(LocalDate.of(2026, 10, 24)), true,
                "opaque-confirmation-1");
        assertThat(evaluator.evaluate(challenge, definition, account("11500"), risk(List.of()),
                UUID.randomUUID(), NOW, confirmed)).isEqualTo(ChallengeStatus.PASSED);
    }

    @Test
    void incompleteTradingEvidenceCannotSatisfyMinimumDays() {
        ChallengeDefinition definition = definition(List.of(
                ChallengePhaseDefinition.create("FIRST", 1, new BigDecimal("0.10"),
                        ProgressionValueSource.BALANCE, 2, policyId, "1.0.0", false)));
        ChallengeInstance challenge = ChallengeInstance.start(UUID.randomUUID(), UUID.randomUUID(), definition,
                definition.startingCapital(), NOW);
        ChallengeProgressionEvidence unavailable = new ChallengeProgressionEvidence(
                new ExecutedTradingEvidence(ExecutedTradingEvidence.Status.UNAVAILABLE, Set.of(), NOW, ""),
                false, "", NOW);

        assertThat(evaluator.evaluate(challenge, definition, account("11000"), risk(List.of()),
                UUID.randomUUID(), NOW, unavailable)).isEqualTo(ChallengeStatus.ACTIVE);
        assertThat(challenge.phaseProgressionState()).isEqualTo(ChallengePhaseProgressionState.ACTIVE);
    }

    @Test
    void rejectsInvalidPhaseOrderingAndConfirmationWithoutEvidence() {
        assertThatThrownBy(() -> definition(List.of(
                ChallengePhaseDefinition.create("FIRST", 2, new BigDecimal("0.10"),
                        ProgressionValueSource.BALANCE, null, policyId, "1.0.0", false))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> definition(List.of(
                phase("FIRST", 1), phase("SECOND", 2), phase("FIRST", 3))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChallengeProgressionEvidence(
                ExecutedTradingEvidence.unavailable(NOW), true, "", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staleEvidenceCannotSatisfyTradingDayObjective() {
        ChallengeDefinition definition = definition(List.of(
                ChallengePhaseDefinition.create("FIRST", 1, new BigDecimal("0.10"),
                        ProgressionValueSource.BALANCE, 1, policyId, "1.0.0", false)));
        ChallengeInstance challenge = ChallengeInstance.start(UUID.randomUUID(), UUID.randomUUID(), definition,
                definition.startingCapital(), NOW);
        ChallengeProgressionEvidence stale = new ChallengeProgressionEvidence(
                new ExecutedTradingEvidence(ExecutedTradingEvidence.Status.STALE,
                        Set.of(), NOW.minusSeconds(3600), "facts-old"), false, "", NOW.minusSeconds(3600),
                "FIRST", false);

        assertThat(evaluator.evaluate(challenge, definition, account("11000"), risk(List.of()),
                UUID.randomUUID(), NOW, stale)).isEqualTo(ChallengeStatus.ACTIVE);
    }

    @Test
    void persistedProviderConfirmationResumesWithoutRecheckingObjectives() {
        ChallengeDefinition definition = definition(List.of(
                ChallengePhaseDefinition.create("FIRST", 1, new BigDecimal("0.10"),
                        ProgressionValueSource.BALANCE, 1, policyId, "1.0.0", true)));
        ChallengeInstance challenge = ChallengeInstance.start(UUID.randomUUID(), UUID.randomUUID(), definition,
                definition.startingCapital(), NOW);
        ChallengeProgressionEvidence local = evidence(Set.of(LocalDate.of(2026, 10, 24)), false, "");
        evaluator.evaluate(challenge, definition, account("11000"), risk(List.of()), UUID.randomUUID(), NOW, local);
        challenge.confirmProviderProgression(NOW, "opaque-confirmation-2");

        assertThat(evaluator.evaluate(challenge, definition, account("10000"), risk(List.of()),
                UUID.randomUUID(), NOW, ChallengeProgressionEvidence.unavailable(NOW)))
                .isEqualTo(ChallengeStatus.PASSED);
    }

    private ChallengeDefinition definition(List<ChallengePhaseDefinition> phases) {
        return ChallengeDefinition.create(UUID.randomUUID(), "1.0.0", "TEST", "CHALLENGE", "PLAN",
                "USD", new BigDecimal("10000"), new BigDecimal("0.10"), ProgressionValueSource.BALANCE,
                SettlementModel.INVENTORY, null, policyId, "1.0.0", "https://example.test", NOW, NOW,
                "test", NOW, phases);
    }

    private ChallengePhaseDefinition phase(String key, int order) {
        return ChallengePhaseDefinition.create(key, order, new BigDecimal("0.10"),
                ProgressionValueSource.BALANCE, null, policyId, "1.0.0", false);
    }

    private Account account(String balance) {
        User user = User.builder().userId(UUID.randomUUID()).username("phase-owner").role(Role.ROLE_USER).build();
        Account account = Account.builder().accountId(UUID.randomUUID()).baseCurrency("USD")
                .startingBalance(new BigDecimal("10000")).equity(new BigDecimal(balance))
                .peakEquity(new BigDecimal(balance)).user(user).build();
        account.addBalance(AccountBalance.builder().asset("USD").amount(new BigDecimal(balance)).build());
        return account;
    }

    private ChallengeProgressionEvidence evidence(Set<LocalDate> days, boolean confirmed, String reference) {
        return new ChallengeProgressionEvidence(
                new ExecutedTradingEvidence(ExecutedTradingEvidence.Status.COMPLETE, days, NOW, "facts-1"),
                confirmed, reference, NOW);
    }

    private RiskValidationResult risk(List<RiskRuleResult> rules) {
        RiskValidationResult result = mock(RiskValidationResult.class);
        TraceMetadata trace = new TraceMetadata(UUID.randomUUID(), UUID.randomUUID(), "test",
                Map.of(policyId.toString(), "1.0.0"), Map.of(), new ContextMetadata(
                UUID.randomUUID(), 1, UUID.randomUUID(), 1, 1, 1, NOW, NOW, NOW, NOW));
        when(result.evaluationStatus()).thenReturn(RiskTypes.EvaluationStatus.COMPLETED);
        when(result.ruleResults()).thenReturn(rules);
        when(result.trace()).thenReturn(trace);
        return result;
    }
}
