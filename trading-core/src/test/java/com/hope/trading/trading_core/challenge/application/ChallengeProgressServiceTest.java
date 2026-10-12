package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountValuationStatus;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeProgressServiceTest {
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID DEFINITION_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    @Mock AccountRepository accounts;
    @Mock ChallengeInstanceRepository instances;
    @Mock ChallengeDefinitionRepository definitions;

    private ChallengeProgressService service;

    @BeforeEach
    void setUp() {
        service = new ChallengeProgressService(accounts, instances, definitions);
    }

    @Test
    void projectsFreshAuthoritativeEquity() {
        Account account = account(SettlementModel.CASH, new BigDecimal("12000"),
                AccountValuationStatus.FRESH, new BigDecimal("12000"));
        ChallengeDefinition definition = definition(ProgressionValueSource.EQUITY);
        stub(account, definition);

        ChallengeProgress progress = service.get(ACCOUNT_ID, OWNER_ID);

        assertThat(progress.currentValue()).isEqualByComparingTo("12000");
        assertThat(progress.currentProfit()).isEqualByComparingTo("2000");
        assertThat(progress.targetProgress()).isEqualByComparingTo("1");
    }

    @Test
    void doesNotFallbackToBalanceWhenEquityIsStale() {
        Account account = account(SettlementModel.CASH, new BigDecimal("15000"),
                AccountValuationStatus.STALE, new BigDecimal("15000"));
        account.setRealizedBalance(new BigDecimal("15000"));
        ChallengeDefinition definition = definition(ProgressionValueSource.EQUITY);
        stub(account, definition);

        ChallengeProgress progress = service.get(ACCOUNT_ID, OWNER_ID);

        assertThat(progress.currentValue()).isNull();
        assertThat(progress.currentProfit()).isNull();
        assertThat(progress.targetProgress()).isNull();
    }

    @Test
    void keepsBalanceProgressionOnRealizedBalance() {
        Account account = account(SettlementModel.CASH, new BigDecimal("15000"),
                AccountValuationStatus.STALE, new BigDecimal("10500"));
        account.setRealizedBalance(new BigDecimal("10500"));
        ChallengeDefinition definition = definition(ProgressionValueSource.BALANCE);
        stub(account, definition);

        ChallengeProgress progress = service.get(ACCOUNT_ID, OWNER_ID);

        assertThat(progress.currentValue()).isEqualByComparingTo("10500");
        assertThat(progress.currentProfit()).isEqualByComparingTo("500");
    }

    private void stub(Account account, ChallengeDefinition definition) {
        ChallengeInstance challenge = org.mockito.Mockito.mock(ChallengeInstance.class);
        when(challenge.id()).thenReturn(UUID.randomUUID());
        when(challenge.definitionId()).thenReturn(DEFINITION_ID);
        when(challenge.definitionVersion()).thenReturn("1.0.0");
        when(challenge.startingCapital()).thenReturn(new BigDecimal("10000"));
        when(challenge.status()).thenReturn(ChallengeStatus.ACTIVE);
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(instances.findTopByAccountIdOrderByStartedAtDesc(ACCOUNT_ID)).thenReturn(Optional.of(challenge));
        when(definitions.findById(any())).thenReturn(Optional.of(definition));
    }

    private static Account account(SettlementModel settlement, BigDecimal equity,
                                   AccountValuationStatus status, BigDecimal realizedBalance) {
        User user = User.builder().userId(OWNER_ID).role(Role.ROLE_USER).build();
        return Account.builder().accountId(ACCOUNT_ID).user(user).settlementModel(settlement)
                .baseCurrency("USD").equity(equity).realizedBalance(realizedBalance)
                .valuationStatus(status).valuedAt(NOW).build();
    }

    private static ChallengeDefinition definition(ProgressionValueSource source) {
        return ChallengeDefinition.create(DEFINITION_ID, "1.0.0", "TEST", "PROP", "PLAN_A", "USD",
                new BigDecimal("10000"), new BigDecimal("0.10"), source, SettlementModel.CASH,
                UUID.randomUUID(), "1.0.0", "https://example.test", NOW, NOW, "test", NOW);
    }
}
