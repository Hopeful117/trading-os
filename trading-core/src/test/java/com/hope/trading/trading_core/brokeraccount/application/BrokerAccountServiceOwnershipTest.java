package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.brokeraccount.api.CreateBrokerAccountRequest;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-3C: protects the broker-account ownership boundary — accounts
 * are scoped to their owner, foreign accounts are invisible, missing
 * accounts fail explicitly, and disconnect/revoke mutate lifecycle state.
 */
class BrokerAccountServiceOwnershipTest {

    private final BrokerAccountRepository repository = mock(BrokerAccountRepository.class);
    private final Instant now = Instant.parse("2026-08-23T10:00:00Z");

    private BrokerAccountService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private BrokerAccount owned;

    @BeforeEach
    void setUp() {
        service = new BrokerAccountService(repository, Clock.fixed(now, ZoneOffset.UTC));
        owned = BrokerAccount.create(
                ownerId, BrokerProvider.KRAKEN, "main", now);
        when(repository.existsById(accountId)).thenReturn(true);
        // Default: the account belongs to ownerId.
        when(repository.findByIdAndOwnerId(accountId, ownerId))
                .thenReturn(Optional.of(owned));
        when(repository.save(any(BrokerAccount.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateBrokerAccountRequest request() {
        return new CreateBrokerAccountRequest(
                BrokerProvider.KRAKEN, "my kraken account");
    }

    @Test
    void creationPersistsAccountOwnedByTheCaller() {
        when(repository.existsById(any(UUID.class))).thenReturn(false);

        var response = service.create(ownerId, request());

        assertThat(response.provider()).isEqualTo(BrokerProvider.KRAKEN);
        assertThat(response.displayName()).isEqualTo("my kraken account");
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
