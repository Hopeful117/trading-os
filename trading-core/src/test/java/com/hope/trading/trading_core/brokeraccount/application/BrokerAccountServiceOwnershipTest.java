package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.brokeraccount.api.CreateBrokerAccountRequest;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.risk.application.RiskProfileValidator;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.RulesRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-3C: protects the broker-account ownership boundary — accounts
 * are scoped to their owner, foreign accounts are invisible, missing
 * accounts fail explicitly, and disconnect/revoke mutate lifecycle state.
 */
class BrokerAccountServiceOwnershipTest {

    private final BrokerAccountRepository repository = mock(BrokerAccountRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final RulesRepository rulesRepository = mock(RulesRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final RiskPersistence riskPersistence = mock(RiskPersistence.class);
    private final RiskProfileValidator riskProfileValidator = mock(RiskProfileValidator.class);
    private final Instant now = Instant.parse("2026-08-23T10:00:00Z");

    private BrokerAccountService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private BrokerAccount owned;

    @BeforeEach
    void setUp() {
        service = new BrokerAccountService(
                repository,
                accountRepository,
                rulesRepository,
                userRepository,
                accountMapper,
                Clock.fixed(now, ZoneOffset.UTC),
                riskPersistence,
                riskProfileValidator
        );
        owned = BrokerAccount.create(
                ownerId, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "main", now);
        when(repository.existsById(accountId)).thenReturn(true);
        // Default: the account belongs to ownerId.
        when(repository.findByIdAndOwnerId(accountId, ownerId))
                .thenReturn(Optional.of(owned));
        when(repository.save(any(BrokerAccount.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateBrokerAccountRequest request() {
        return new CreateBrokerAccountRequest(
                BrokerProvider.KRAKEN, "my kraken account", ExecutionMode.LIVE, null, null);
    }

    @Test
    void creationPersistsAccountOwnedByTheCaller() {
        when(repository.existsById(any(UUID.class))).thenReturn(false);

        var response = service.create(ownerId, request());

        assertThat(response.provider()).isEqualTo(BrokerProvider.KRAKEN);
        assertThat(response.displayName()).isEqualTo("my kraken account");
    }

    @Test
    void paperCreationPersistsFinancialAccountForTheCaller() {
        UUID paperOwner = UUID.randomUUID();
        User user = User.builder().userId(paperOwner).build();
        Rules rules = Rules.builder().name("paper-rules").build();
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        when(rulesRepository.findByName("Default Paper Trading Rules")).thenReturn(Optional.of(rules));
        when(userRepository.getReferenceById(paperOwner)).thenReturn(user);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID profileId = UUID.randomUUID();
        when(riskPersistence.profile(profileId, "1.0.0"))
                .thenReturn(Optional.of(mock(RiskPersistence.Profile.class)));

        var response = service.create(paperOwner,
                new CreateBrokerAccountRequest(BrokerProvider.KRAKEN, "paper", ExecutionMode.PAPER,
                        new java.math.BigDecimal("10000"),
                        new com.hope.trading.trading_core.brokeraccount.api.RiskProfileReference(profileId, "1.0.0")));

        assertThat(response.executionMode()).isEqualTo(ExecutionMode.PAPER);
        var accountCaptor = org.mockito.ArgumentCaptor.forClass(com.hope.trading.trading_core.model.Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getUser()).isSameAs(user);
        assertThat(accountCaptor.getValue().getEquity()).isEqualByComparingTo("10000");
        assertThat(accountCaptor.getValue().getBrokerAccountId()).isNotNull();
    }

    @Test
    void paperCreationRequiresAnExplicitRiskProfile() {
        when(repository.existsById(any(UUID.class))).thenReturn(false);

        assertThatThrownBy(() -> service.create(ownerId,
                new CreateBrokerAccountRequest(BrokerProvider.KRAKEN, "paper", ExecutionMode.PAPER,
                        new java.math.BigDecimal("10000"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("riskProfile is required");
        verify(repository, never()).save(any(BrokerAccount.class));
    }

    @Test
    void paperCreationRejectsUnknownRiskProfile() {
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        UUID profileId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(ownerId,
                new CreateBrokerAccountRequest(BrokerProvider.KRAKEN, "paper", ExecutionMode.PAPER,
                        new java.math.BigDecimal("10000"),
                        new com.hope.trading.trading_core.brokeraccount.api.RiskProfileReference(profileId, "1.0.0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
        verify(repository, never()).save(any(BrokerAccount.class));
    }

    @Test
    void ownerCanReadHisOwnAccount() {
        var response = service.get(ownerId, accountId);

        assertThat(response.displayName()).isEqualTo("main");
    }

    @Test
    void foreignOwnerCannotSeeOrDisconnectAnAccountHeDoesNotOwn() {
        UUID intruder = UUID.randomUUID();
        when(repository.findByIdAndOwnerId(accountId, intruder))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(intruder, accountId))
                .isInstanceOf(com.hope.trading.trading_core.brokeraccount.application
                        .BrokerAccountOwnershipException.class);
        assertThatThrownBy(() -> service.disconnect(intruder, accountId))
                .isInstanceOf(com.hope.trading.trading_core.brokeraccount.application
                        .BrokerAccountOwnershipException.class);
    }

    @Test
    void unknownAccountFailsExplicitly() {
        UUID unknown = UUID.randomUUID();
        when(repository.existsById(unknown)).thenReturn(false);

        assertThatThrownBy(() -> service.get(ownerId, unknown))
                .isInstanceOf(com.hope.trading.trading_core.brokeraccount.application
                        .BrokerAccountNotFoundException.class);
    }

    @Test
    void connectedAccountCanBeDisconnectedByItsOwner() {
        owned.markPendingValidation(now);
        owned.markConnected(
                new com.hope.trading.trading_core.brokeraccount.domain.CredentialReference(
                        UUID.randomUUID()),
                "ext-account-1", now);

        var response = service.disconnect(ownerId, accountId);

        assertThat(response.connectionStatus().name()).isEqualTo("DISCONNECTED");
    }

    @Test
    void updateStatusToConnectedFromPendingValidation() {
        owned.markPendingValidation(now);
        var response = service.updateStatus(
                ownerId, accountId,
                com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus.CONNECTED,
                UUID.randomUUID(), "ext-123", now);

        assertThat(response.connectionStatus()).isEqualTo(
                com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus.CONNECTED);
    }

    @Test
    void updateStatusToInvalidCredentialsFromPendingValidation() {
        owned.markPendingValidation(now);
        var response = service.updateStatus(
                ownerId, accountId,
                com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus.INVALID_CREDENTIALS,
                null, null, now);

        assertThat(response.connectionStatus()).isEqualTo(
                com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus.INVALID_CREDENTIALS);
    }

    @Test
    void updateStatusToTemporarilyUnavailableFromPendingValidation() {
        owned.markPendingValidation(now);
        var response = service.updateStatus(
                ownerId, accountId,
                com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE,
                null, null, now);

        assertThat(response.connectionStatus()).isEqualTo(
                com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE);
    }

    @Test
    void updateStatusToDisconnectedFromConnected() {
        owned.markPendingValidation(now);
        owned.markConnected(
                new com.hope.trading.trading_core.brokeraccount.domain.CredentialReference(
                        UUID.randomUUID()),
                "ext-1", now);
        var response = service.updateStatus(
                ownerId, accountId,
                com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus.DISCONNECTED,
                null, null, now);

        assertThat(response.connectionStatus()).isEqualTo(
                com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus.DISCONNECTED);
    }

    @Test
    void updateStatusToRevokedFromConnected() {
        owned.markPendingValidation(now);
        owned.markConnected(
                new com.hope.trading.trading_core.brokeraccount.domain.CredentialReference(
                        UUID.randomUUID()),
                "ext-1", now);
        var response = service.updateStatus(
                ownerId, accountId,
                com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus.REVOKED,
                null, null, now);

        assertThat(response.connectionStatus()).isEqualTo(
                com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus.REVOKED);
    }

    @Test
    void updateStatusWithUnsupportedStatusThrows() {
        assertThatThrownBy(() -> service.updateStatus(
                ownerId, accountId,
                com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus.REAUTHENTICATION_REQUIRED,
                null, null, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }
}
