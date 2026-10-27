package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActiveChallengeMonitoringServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    @Test
    void monitorsLiveAndPaperChallengesAndContinuesAfterOneFailure() {
        Candidate paper = candidate(ExecutionMode.PAPER, "paper-a");
        Candidate failed = candidate(ExecutionMode.PAPER, "paper-b");
        Candidate live = candidate(ExecutionMode.LIVE, "live");
        ChallengeInstanceRepository challenges = mock(ChallengeInstanceRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        ChallengeReevaluationService reevaluation = mock(ChallengeReevaluationService.class);
        when(challenges.findFirstPageByStatus(eq(ChallengeStatus.ACTIVE), any()))
                .thenReturn(List.of(paper.challenge, failed.challenge, live.challenge));
        when(challenges.findNextPageByStatus(eq(ChallengeStatus.ACTIVE), eq(failed.challenge.startedAt()),
                eq(live.challenge.id()), any())).thenReturn(List.of());
        when(accounts.findById(paper.account.getAccountId())).thenReturn(Optional.of(paper.account));
        when(accounts.findById(failed.account.getAccountId())).thenReturn(Optional.of(failed.account));
        when(accounts.findById(live.account.getAccountId())).thenReturn(Optional.of(live.account));
        when(brokers.findById(paper.broker.id())).thenReturn(Optional.of(paper.broker));
        when(brokers.findById(failed.broker.id())).thenReturn(Optional.of(failed.broker));
        when(brokers.findById(live.broker.id())).thenReturn(Optional.of(live.broker));
        when(reevaluation.reevaluate(paper.challenge.id())).thenReturn(ChallengeStatus.ACTIVE);
        when(reevaluation.reevaluate(live.challenge.id())).thenReturn(ChallengeStatus.ACTIVE);
        doThrow(new IllegalStateException("valuation unavailable"))
                .when(reevaluation).reevaluate(failed.challenge.id());

        ActiveChallengeMonitoringService service = service(challenges, accounts, brokers, reevaluation, 50);

        ActiveChallengeMonitoringService.MonitoringCycle result = service.monitor();

        assertThat(result.discovered()).isEqualTo(3);
        assertThat(result.evaluated()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        verify(reevaluation).reevaluate(paper.challenge.id());
        verify(reevaluation).reevaluate(failed.challenge.id());
        verify(reevaluation).reevaluate(live.challenge.id());
    }

    @Test
    void usesStableBatchesAndDoesNotRediscoverTerminalChallenges() {
        Candidate first = candidate(ExecutionMode.PAPER, "first");
        Candidate second = candidate(ExecutionMode.PAPER, "second");
        ChallengeInstanceRepository challenges = mock(ChallengeInstanceRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        ChallengeReevaluationService reevaluation = mock(ChallengeReevaluationService.class);
        when(challenges.findFirstPageByStatus(eq(ChallengeStatus.ACTIVE), any()))
                .thenReturn(List.of(first.challenge));
        when(challenges.findNextPageByStatus(eq(ChallengeStatus.ACTIVE), eq(first.challenge.startedAt()),
                eq(first.challenge.id()), any())).thenReturn(List.of(second.challenge));
        when(challenges.findNextPageByStatus(eq(ChallengeStatus.ACTIVE), eq(second.challenge.startedAt()),
                eq(second.challenge.id()), any())).thenReturn(List.of());
        for (Candidate candidate : List.of(first, second)) {
            when(accounts.findById(candidate.account.getAccountId())).thenReturn(Optional.of(candidate.account));
            when(brokers.findById(candidate.broker.id())).thenReturn(Optional.of(candidate.broker));
            when(reevaluation.reevaluate(candidate.challenge.id())).thenReturn(ChallengeStatus.ACTIVE);
        }

        ActiveChallengeMonitoringService service = service(challenges, accounts, brokers, reevaluation, 1);

        assertThat(service.monitor().evaluated()).isEqualTo(2);
        verify(reevaluation).reevaluate(first.challenge.id());
        verify(reevaluation).reevaluate(second.challenge.id());
    }

    private ActiveChallengeMonitoringService service(ChallengeInstanceRepository challenges,
                                                     AccountRepository accounts,
                                                     BrokerAccountRepository brokers,
                                                     ChallengeReevaluationService reevaluation,
                                                     int batchSize) {
        ChallengeMonitoringProperties properties = new ChallengeMonitoringProperties();
        properties.setBatchSize(batchSize);
        return new ActiveChallengeMonitoringService(challenges, accounts, brokers, reevaluation, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Candidate candidate(ExecutionMode mode, String username) {
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        User owner = User.builder().userId(ownerId).username(username).role(Role.ROLE_USER).build();
        BrokerAccount broker = BrokerAccount.create(ownerId, BrokerProvider.KRAKEN, mode, username, NOW);
        Account account = Account.builder().accountId(accountId).brokerAccountId(broker.id()).broker("KRAKEN")
                .name(username).baseCurrency("USD").startingBalance(new BigDecimal("50000"))
                .equity(new BigDecimal("50000")).peakEquity(new BigDecimal("50000"))
                .settlementModel(SettlementModel.CASH).user(owner).build();
        ChallengeDefinition definition = ChallengeDefinition.create(UUID.randomUUID(), "1.0.0", "TEST", "PAPER",
                username, "USD", new BigDecimal("50000"), new BigDecimal("0.09"),
                ProgressionValueSource.EQUITY, SettlementModel.CASH, UUID.randomUUID(), "1.0.0",
                "https://example.test", NOW, NOW, "test", NOW);
        ChallengeInstance challenge = ChallengeInstance.start(UUID.randomUUID(), accountId, definition,
                definition.startingCapital(), NOW);
        return new Candidate(account, broker, challenge);
    }

    private record Candidate(Account account, BrokerAccount broker, ChallengeInstance challenge) { }
}
