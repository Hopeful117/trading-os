package com.hope.trading.trading_core.risk.application;

import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.risk.application.port.RiskFactsProvider;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountRiskMonitoringServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    @Test
    void capturesAuthoritativeObservedBalanceForFirstRiskDayBaseline() {
        UUID accountId = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        User owner = User.builder().userId(UUID.randomUUID()).username("risk-owner")
                .role(Role.ROLE_USER).build();
        Account account = Account.builder().accountId(accountId).brokerAccountId(brokerId).broker("KRAKEN")
                .baseCurrency("USD").startingBalance(new BigDecimal("5000")).equity(new BigDecimal("5100"))
                .user(owner).build();
        BrokerAccount broker = BrokerAccount.create(owner.getUserId(), BrokerProvider.KRAKEN, ExecutionMode.PAPER,
                "paper", NOW);
        ChallengeDefinition definition = ChallengeDefinition.create(UUID.randomUUID(), "1.0.0", "TEST", "PAPER",
                "PLAN", "USD", new BigDecimal("5000"), new BigDecimal("0.10"),
                ProgressionValueSource.BALANCE,
                com.hope.trading.trading_core.shared.domain.model.SettlementModel.INVENTORY,
                policyId, "1.0.0", "https://example.test", NOW, NOW, "test", NOW);
        ChallengeInstance challenge = ChallengeInstance.start(UUID.randomUUID(), accountId, definition,
                definition.startingCapital(), NOW);

        com.hope.trading.trading_core.repository.AccountRepository accountRepository = mock(
                com.hope.trading.trading_core.repository.AccountRepository.class);
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        RiskFactsProvider facts = mock(RiskFactsProvider.class);
        RiskPersistence persistence = mock(RiskPersistence.class);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(brokers.findById(brokerId)).thenReturn(Optional.of(broker));
        when(persistence.configuration(accountId)).thenReturn(Optional.of(new RiskPersistence.AccountConfiguration(
                accountId, brokerId, "UTC", "USD", accountId, "00:30")));
        when(persistence.profile(policyId, "1.0.0")).thenReturn(Optional.of(profile(policyId)));
        when(facts.load(same(account), same(broker), eq(broker.id()), any(), any())).thenReturn(
                new RiskFactsProvider.Snapshot(broker.id(), 1, NOW, true, List.of(),
                        Map.of("USD", new BigDecimal("5100")),
                        new RiskFactsProvider.Account("USD", new BigDecimal("5100"),
                                new BigDecimal("5100"), BigDecimal.ZERO, new BigDecimal("5000")),
                        List.of(), List.of(), List.of(), "facts"));
        when(persistence.write(any())).thenReturn("{}");
        when(persistence.component(any(), any(), any(), any(), any())).thenReturn(1L);
        when(persistence.context(any(), any(), any())).thenReturn(1L);
        AtomicReference<BigDecimal> captured = new AtomicReference<>();
        when(persistence.baseline(any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(5));
            return new RiskPersistence.Baseline(1, captured.get(), "USD", invocation.getArgument(2),
                    invocation.getArgument(3), 1, "baseline");
        });

        AccountRiskMonitoringService service = new AccountRiskMonitoringService(accountRepository, brokers, facts,
                persistence, new RiskProfileValidator(), Clock.fixed(NOW, ZoneOffset.UTC));

        service.evaluate(challenge, account, NOW);
        service.evaluate(challenge, account, NOW.plusSeconds(5));

        assertThat(captured.get()).isEqualByComparingTo("5100");
        verify(persistence, org.mockito.Mockito.times(2)).accountMonitoringEvaluation(any(), eq(owner.getUserId()), any(), eq(accountId),
                any(), any(), any(), anyLong(), any(), eq(new BigDecimal("5100")), any());
    }

    private RiskPersistence.Profile profile(UUID id) {
        return new RiskPersistence.Profile(id, "1.0.0", "policy", "1.0.0", "PLATFORM", NOW, "source", null,
                null, List.of(
                new RiskPersistence.ProfileRule("MAX_POSITION_RISK", "1.0.0", "POSITION", "BLOCKING", 10,
                        new BigDecimal("0.01"), "source"),
                new RiskPersistence.ProfileRule("MAX_EXPOSURE", "1.0.0", "PORTFOLIO", "BLOCKING", 20,
                        new BigDecimal("0.03"), "source"),
                new RiskPersistence.ProfileRule("DAILY_DRAWDOWN", "1.0.0", "ACCOUNT", "BLOCKING", 30,
                        new BigDecimal("0.03"), "source")));
    }
}
