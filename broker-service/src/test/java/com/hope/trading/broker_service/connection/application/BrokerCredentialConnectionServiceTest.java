package com.hope.trading.broker_service.connection.application;

import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.connection.integration.TradingCoreBrokerAccountClient;
import com.hope.trading.broker_service.credential.application.BrokerCredentialSource;
import com.hope.trading.broker_service.credential.application.BrokerCredentialValidator;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.credential.domain.CredentialValidationOutcome;
import com.hope.trading.broker_service.credential.domain.CredentialValidationResult;
import com.hope.trading.broker_service.credential.domain.SafeProviderDiagnostics;
import com.hope.trading.broker_service.secret.application.SecretRevoker;
import com.hope.trading.broker_service.secret.application.SecretRotator;
import com.hope.trading.broker_service.secret.application.SecretWriter;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerCredentialConnectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");
    @Mock BrokerConnectionRepository repository;
    @Mock TradingCoreBrokerAccountClient tradingCore;
    @Mock SecretWriter writer;
    @Mock SecretRotator rotator;
    @Mock SecretRevoker revoker;
    @Mock BrokerCredentialSource source;

    @Test
    void invalidCredentialsAreNeverPersisted() {
        UUID owner = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(tradingCore.findOwned(accountId, "Bearer fake")).thenReturn(account(accountId));
        when(repository.findByBrokerAccountId(accountId)).thenReturn(Optional.empty());
        BrokerCredentialConnectionService service = service(result(CredentialValidationOutcome.INVALID_CREDENTIALS));

        try (CredentialMaterial material = material()) {
            var response = service.connect(owner, accountId, material, "Bearer fake");
            assertEquals(BrokerConnectionStatus.INVALID_CREDENTIALS, response.connectionStatus());
        }
        verify(writer, never()).write(any(), any());
    }

    @Test
    void validCredentialsAreWrittenOnlyAfterValidation() {
        UUID owner = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(tradingCore.findOwned(accountId, "Bearer fake")).thenReturn(account(accountId));
        when(repository.findByBrokerAccountId(accountId)).thenReturn(Optional.empty());
        when(writer.write(any(), any())).thenReturn(new CredentialReference(UUID.randomUUID()));
        BrokerCredentialConnectionService service = service(result(CredentialValidationOutcome.VALID));

        try (CredentialMaterial material = material()) {
            assertEquals(BrokerConnectionStatus.CONNECTED,
                    service.connect(owner, accountId, material, "Bearer fake").connectionStatus());
        }
        verify(writer).write(any(), any());
    }

    @Test
    void failedRotationKeepsExistingActiveReference() {
        UUID owner = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        BrokerConnection connection = BrokerConnection.create(accountId, owner, BrokerProviderId.KRAKEN, NOW);
        connection.pending(NOW);
        connection.connected(active, readPermissions(), null, "••••1234", NOW);
        when(tradingCore.findOwned(accountId, "Bearer fake")).thenReturn(account(accountId));
        when(repository.findByBrokerAccountIdAndOwnerId(accountId, owner)).thenReturn(Optional.of(connection));
        BrokerCredentialConnectionService service = service(result(CredentialValidationOutcome.INVALID_CREDENTIALS));

        try (CredentialMaterial replacement = material()) {
            service.rotate(owner, accountId, replacement, "Bearer fake");
        }
        assertEquals(active, connection.activeCredentialReference());
        assertEquals(BrokerConnectionStatus.CONNECTED, connection.technicalStatus());
        verify(rotator, never()).rotate(any(), any(), any());
    }

    private BrokerCredentialConnectionService service(CredentialValidationResult result) {
        BrokerCredentialValidator validator = new BrokerCredentialValidator() {
            @Override public BrokerProviderId provider() { return BrokerProviderId.KRAKEN; }
            @Override public CredentialValidationResult validate(CredentialMaterial credentials) { return result; }
        };
        return new BrokerCredentialConnectionService(repository, tradingCore, List.of(validator), writer,
                rotator, revoker, source, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private TradingCoreBrokerAccountClient.BrokerAccountContract account(UUID id) {
        return new TradingCoreBrokerAccountClient.BrokerAccountContract(id, BrokerProviderId.KRAKEN,
                "Kraken", null, BrokerConnectionStatus.CREATED, null);
    }

    private CredentialValidationResult result(CredentialValidationOutcome outcome) {
        Set<BrokerPermission> permissions = outcome == CredentialValidationOutcome.VALID
                ? readPermissions() : Set.of();
        return new CredentialValidationResult(outcome, null, permissions, Set.of(), NOW,
                new SafeProviderDiagnostics("SAFE", "Safe validation message"));
    }

    private Set<BrokerPermission> readPermissions() {
        return Set.of(BrokerPermission.READ_ACCOUNT, BrokerPermission.READ_BALANCES,
                BrokerPermission.READ_POSITIONS, BrokerPermission.READ_ORDERS,
                BrokerPermission.READ_TRADE_HISTORY);
    }

    private CredentialMaterial material() {
        return new CredentialMaterial("FAKE_API_KEY_1234".toCharArray(),
                "RkFLRV9TRU5USU5FTF9TRUNSRVQ=".toCharArray(), null);
    }
}
