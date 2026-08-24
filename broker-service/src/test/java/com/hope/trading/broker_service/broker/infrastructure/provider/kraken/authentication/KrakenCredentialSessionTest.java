package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication;

import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerAuthenticationException;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.credential.application.BrokerCredentialSource;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KrakenCredentialSessionTest {
    @Mock private BrokerConnectionRepository connections;
    @Mock private BrokerCredentialSource credentials;
    @InjectMocks private KrakenCredentialSession session;

    @Test
    void withCredentialsThrowsWhenConnectionNotFound() {
        UUID accountId = UUID.randomUUID();
        when(connections.findByBrokerAccountId(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> session.withCredentials(accountId, m -> "result"))
                .isInstanceOf(BrokerAuthenticationException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    void withCredentialsThrowsWhenStatusNotConnected() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Instant now = Instant.now();

        BrokerConnection connection = BrokerConnection.create(accountId, owner, BrokerProviderId.KRAKEN, now);
        when(connections.findByBrokerAccountId(accountId)).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> session.withCredentials(accountId, m -> "result"))
                .isInstanceOf(BrokerAuthenticationException.class)
                .hasMessageContaining("not ready");
    }

    @Test
    void withCredentialsThrowsWhenActiveCredentialReferenceIsNull() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Instant now = Instant.now();

        BrokerConnection connection = BrokerConnection.create(accountId, owner, BrokerProviderId.KRAKEN, now);
        connection.connected(UUID.randomUUID(), Set.of(BrokerPermission.READ_ACCOUNT, BrokerPermission.READ_BALANCES), "ext-123", "hint", now);

        Field refField = BrokerConnection.class.getDeclaredField("activeCredentialReference");
        refField.setAccessible(true);
        refField.set(connection, null);

        when(connections.findByBrokerAccountId(accountId)).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> session.withCredentials(accountId, m -> "result"))
                .isInstanceOf(BrokerAuthenticationException.class)
                .hasMessageContaining("not ready");
    }

    @Test
    void withCredentialsAppliesFunctionWithCredentialMaterialOnSuccess() {
        UUID accountId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID credRef = UUID.randomUUID();
        Instant now = Instant.now();

        BrokerConnection connection = BrokerConnection.create(accountId, owner, BrokerProviderId.KRAKEN, now);
        connection.connected(credRef, Set.of(BrokerPermission.READ_ACCOUNT, BrokerPermission.READ_BALANCES), "ext-123", "hint", now);

        CredentialMaterial material = new CredentialMaterial(
                "valid-api-key-12345678".toCharArray(),
                "valid-api-secret-value-1234567890123".toCharArray(),
                null
        );

        when(connections.findByBrokerAccountId(accountId)).thenReturn(Optional.of(connection));
        when(credentials.resolve(any(CredentialReference.class))).thenReturn(material);

        String result = session.withCredentials(accountId, m -> "success:" + new String(m.copyApiKey()));

        assertThat(result).isEqualTo("success:valid-api-key-12345678");
        verify(credentials).resolve(argThat(ref -> ref.value().equals(credRef)));
    }
}
