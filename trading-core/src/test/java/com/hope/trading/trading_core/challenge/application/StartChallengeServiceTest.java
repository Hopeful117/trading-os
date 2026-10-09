package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartChallengeServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void startsChallengeAfterOwnershipAndExactReferenceValidationWithoutMutatingAccount() {
        AccountRepository accounts = mock(AccountRepository.class);
        ChallengeDefinitionRepository definitions = mock(ChallengeDefinitionRepository.class);
        ChallengeInstanceRepository instances = mock(ChallengeInstanceRepository.class);
        RiskPersistence riskPersistence = mock(RiskPersistence.class);
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        User owner = User.builder().userId(ownerId).username("owner").role(Role.ROLE_USER).build();
        Account account = Account.builder().accountId(accountId).baseCurrency("USD")
                .startingBalance(new BigDecimal("10000")).equity(new BigDecimal("10000"))
                .peakEquity(new BigDecimal("10000")).user(owner).build();
        ChallengeDefinition definition = definition(definitionId, policyId);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(definitions.findById(new ChallengeDefinitionKey(definitionId, "1.0.0")))
                .thenReturn(Optional.of(definition));
        when(riskPersistence.profile(policyId, "1.0.0")).thenReturn(Optional.of(new RiskPersistence.Profile(
                policyId, "1.0.0", "policy", "1.0.0", "PLATFORM", NOW, "source", null, null, java.util.List.of())));
        when(instances.existsByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE)).thenReturn(false);
        when(instances.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StartChallengeService service = new StartChallengeService(accounts, definitions, instances, riskPersistence,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var started = service.start(new StartChallengeCommand(ownerId, accountId, definitionId, "1.0.0"));

        assertThat(started.status()).isEqualTo(ChallengeStatus.ACTIVE);
        assertThat(started.accountId()).isEqualTo(accountId);
        assertThat(started.startingCapital()).isEqualByComparingTo("10000");
        assertThat(account.getStartingBalance()).isEqualByComparingTo("10000");
        verify(instances).save(any());
    }

    @Test
    void rejectsSecondActiveChallenge() {
        AccountRepository accounts = mock(AccountRepository.class);
        ChallengeDefinitionRepository definitions = mock(ChallengeDefinitionRepository.class);
        ChallengeInstanceRepository instances = mock(ChallengeInstanceRepository.class);
        RiskPersistence riskPersistence = mock(RiskPersistence.class);
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        User owner = User.builder().userId(ownerId).username("owner").role(Role.ROLE_USER).build();
        Account account = Account.builder().accountId(accountId).baseCurrency("USD")
                .startingBalance(new BigDecimal("10000")).user(owner).build();
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(instances.existsByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE)).thenReturn(true);

        StartChallengeService service = new StartChallengeService(accounts, definitions, instances, riskPersistence,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class,
                () -> service.start(new StartChallengeCommand(ownerId, accountId, UUID.randomUUID(), "1.0.0")));
        verify(definitions, never()).findById(any());
        verify(instances, never()).save(any());
    }

    @Test
    void rejectsAccountWithIncompatibleStartingCapital() {
        AccountRepository accounts = mock(AccountRepository.class);
        ChallengeDefinitionRepository definitions = mock(ChallengeDefinitionRepository.class);
        ChallengeInstanceRepository instances = mock(ChallengeInstanceRepository.class);
        RiskPersistence riskPersistence = mock(RiskPersistence.class);
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        User owner = User.builder().userId(ownerId).username("owner").role(Role.ROLE_USER).build();
        Account account = Account.builder().accountId(accountId).baseCurrency("USD")
                .startingBalance(new BigDecimal("5000")).user(owner).build();
        ChallengeDefinition definition = definition(definitionId, policyId);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(definitions.findById(new ChallengeDefinitionKey(definitionId, "1.0.0")))
                .thenReturn(Optional.of(definition));
        when(riskPersistence.profile(policyId, "1.0.0")).thenReturn(Optional.of(new RiskPersistence.Profile(
                policyId, "1.0.0", "policy", "1.0.0", "PLATFORM", NOW, "source", null, null, java.util.List.of())));
        when(instances.existsByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE)).thenReturn(false);

        StartChallengeService service = new StartChallengeService(accounts, definitions, instances, riskPersistence,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class,
                () -> service.start(new StartChallengeCommand(ownerId, accountId, definitionId, "1.0.0")));
        verify(instances, never()).save(any());
    }

    private static ChallengeDefinition definition(UUID id, UUID policyId) {
        return ChallengeDefinition.create(id, "1.0.0", "TEST_PROVIDER", "TEST_CHALLENGE", "PLAN_A", "USD",
                new BigDecimal("10000"), new BigDecimal("0.12"),
                com.hope.trading.trading_core.challenge.domain.ProgressionValueSource.BALANCE, policyId,
                "1.0.0", "https://example.test/definition", NOW, NOW, "source", NOW);
    }
}
