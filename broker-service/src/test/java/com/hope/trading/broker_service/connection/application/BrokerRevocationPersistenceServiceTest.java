package com.hope.trading.broker_service.connection.application;

import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.connection.domain.BrokerRevocationNotification;
import com.hope.trading.broker_service.connection.integration.TradingCoreBrokerAccountClient;
import com.hope.trading.broker_service.secret.application.SecretRevoker;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerRevocationPersistenceServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    @Mock BrokerConnectionRepository connections;
    @Mock BrokerRevocationNotificationRepository notifications;
    @Mock TradingCoreBrokerAccountClient tradingCore;
    @Mock SecretRevoker revoker;

    @Test
    void revokeLocallyCommitsTerminalConnectionAndPendingNotificationData() {
        UUID owner = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID activeReference = UUID.randomUUID();
        BrokerConnection connection = BrokerConnection.create(accountId, owner, BrokerProviderId.KRAKEN, NOW);
        connection.pending(NOW);
        connection.connected(activeReference, Set.of(BrokerPermission.READ_ACCOUNT), "external-1", "hint", NOW);
        BrokerRevocationNotification notification = BrokerRevocationNotification.pending(
                accountId, owner, "external-1", NOW);

        when(connections.findByBrokerAccountIdAndOwnerIdForUpdate(accountId, owner)).thenReturn(Optional.of(connection));
        when(notifications.findByBrokerAccountId(accountId)).thenReturn(Optional.empty());
        when(notifications.save(any(BrokerRevocationNotification.class))).thenReturn(notification);

        UUID result = service().revokeLocally(owner, accountId, "Bearer user");

        assertThat(result).isEqualTo(notification.id());
        assertThat(connection.technicalStatus()).isEqualTo(BrokerConnectionStatus.REVOKED);
        assertThat(connection.activeCredentialReference()).isNull();
        verify(revoker).revoke(new CredentialReference(activeReference), "USER_REQUEST");
        verify(connections).save(connection);
        verify(connections).findByBrokerAccountIdAndOwnerIdForUpdate(accountId, owner);
        verify(notifications).save(any(BrokerRevocationNotification.class));
    }

    @Test
    void existingNotificationIsReusedForDuplicateRevoke() {
        UUID owner = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BrokerConnection connection = BrokerConnection.create(accountId, owner, BrokerProviderId.KRAKEN, NOW);
        connection.revoke(NOW);
        BrokerRevocationNotification notification = BrokerRevocationNotification.pending(
                accountId, owner, null, NOW);

        when(connections.findByBrokerAccountIdAndOwnerIdForUpdate(accountId, owner)).thenReturn(Optional.of(connection));
        when(notifications.findByBrokerAccountId(accountId)).thenReturn(Optional.of(notification));

        assertThat(service().revokeLocally(owner, accountId, "Bearer user"))
                .isEqualTo(notification.id());
        verify(notifications, org.mockito.Mockito.never()).save(any());
    }

    private BrokerRevocationPersistenceService service() {
        return new BrokerRevocationPersistenceService(connections, notifications, tradingCore, revoker,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
