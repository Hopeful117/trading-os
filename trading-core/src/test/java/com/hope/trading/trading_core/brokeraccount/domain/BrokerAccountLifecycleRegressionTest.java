package com.hope.trading.trading_core.brokeraccount.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Comprehensive regression tests for BrokerAccount lifecycle transitions.
 * Protects: all valid transitions, invalid transitions, credential handling,
 * ownership invariants, version tracking.
 */
class BrokerAccountLifecycleRegressionTest {

    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID CREDENTIAL_REF = UUID.randomUUID();

    @Test
    void createSetsInitialState() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test Account", NOW);

        assertEquals(BrokerConnectionStatus.CREATED, account.connectionStatus());
        assertEquals(OWNER, account.ownerId());
        assertEquals(BrokerProvider.KRAKEN, account.provider());
        assertEquals("Test Account", account.displayName());
        assertNull(account.externalAccountId());
        assertNull(account.credentialReference());
        assertNull(account.lastValidatedAt());
        assertEquals(NOW, account.createdAt());
        assertEquals(NOW, account.updatedAt());
    }

    @Test
    void createValidatesOwnerIdRequired() {
        assertThrows(NullPointerException.class,
                () -> BrokerAccount.create(null, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "name", NOW));
    }

    @Test
    void createValidatesProviderRequired() {
        assertThrows(NullPointerException.class,
                () -> BrokerAccount.create(OWNER, null, ExecutionMode.LIVE, "name", NOW));
    }

    @Test
    void createValidatesDisplayNameRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, null, NOW));
    }

    @Test
    void createValidatesDisplayNameNotBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "   ", NOW));
    }

    @Test
    void createValidatesDisplayNameMaxLength() {
        String longName = "a".repeat(81);
        assertThrows(IllegalArgumentException.class,
                () -> BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, longName, NOW));
    }

    @Test
    void createTrimsDisplayName() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "  Test  ", NOW);
        assertEquals("Test", account.displayName());
    }

    @Test
    void validTransitionCreatedToPendingValidation() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);

        assertEquals(BrokerConnectionStatus.PENDING_VALIDATION, account.connectionStatus());
    }

    @Test
    void validTransitionPendingValidationToConnected() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), "ext-123", NOW);

        assertEquals(BrokerConnectionStatus.CONNECTED, account.connectionStatus());
        assertEquals(CREDENTIAL_REF, account.credentialReference().value());
        assertEquals("ext-123", account.externalAccountId());
        assertEquals(NOW, account.lastValidatedAt());
    }

    @Test
    void validTransitionPendingValidationToInvalidCredentials() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markInvalidCredentials(NOW);

        assertEquals(BrokerConnectionStatus.INVALID_CREDENTIALS, account.connectionStatus());
    }

    @Test
    void validTransitionPendingValidationToInsufficientPermissions() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markInsufficientPermissions(NOW);

        assertEquals(BrokerConnectionStatus.INSUFFICIENT_PERMISSIONS, account.connectionStatus());
    }

    @Test
    void validTransitionPendingValidationToTemporarilyUnavailable() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markTemporarilyUnavailable(NOW);

        assertEquals(BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE, account.connectionStatus());
    }

    @Test
    void validTransitionConnectedToPendingValidation() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.markPendingValidation(NOW);

        assertEquals(BrokerConnectionStatus.PENDING_VALIDATION, account.connectionStatus());
    }

    @Test
    void validTransitionConnectedToTemporarilyUnavailable() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.markTemporarilyUnavailable(NOW);

        assertEquals(BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE, account.connectionStatus());
    }

    @Test
    void validTransitionConnectedToReauthenticationRequired() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.markReauthenticationRequired(NOW);

        assertEquals(BrokerConnectionStatus.REAUTHENTICATION_REQUIRED, account.connectionStatus());
    }

    @Test
    void validTransitionConnectedToDisconnected() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.disconnect(NOW);

        assertEquals(BrokerConnectionStatus.DISCONNECTED, account.connectionStatus());
    }

    @Test
    void validTransitionConnectedToRevoked() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.markRevoked(NOW);

        assertEquals(BrokerConnectionStatus.REVOKED, account.connectionStatus());
        assertNull(account.credentialReference());
    }

    @Test
    void validTransitionInvalidCredentialsToPendingValidation() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markInvalidCredentials(NOW);
        account.markPendingValidation(NOW);

        assertEquals(BrokerConnectionStatus.PENDING_VALIDATION, account.connectionStatus());
    }

    @Test
    void validTransitionInsufficientPermissionsToPendingValidation() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markInsufficientPermissions(NOW);
        account.markPendingValidation(NOW);

        assertEquals(BrokerConnectionStatus.PENDING_VALIDATION, account.connectionStatus());
    }

    @Test
    void validTransitionTemporarilyUnavailableToConnected() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.markTemporarilyUnavailable(NOW);
        account.markConnected(new CredentialReference(UUID.randomUUID()), null, NOW);

        assertEquals(BrokerConnectionStatus.CONNECTED, account.connectionStatus());
    }

    @Test
    void validTransitionTemporarilyUnavailableToReauthenticationRequired() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.markTemporarilyUnavailable(NOW);
        account.markReauthenticationRequired(NOW);

        assertEquals(BrokerConnectionStatus.REAUTHENTICATION_REQUIRED, account.connectionStatus());
    }

    @Test
    void validTransitionTemporarilyUnavailableToPendingValidation() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.markTemporarilyUnavailable(NOW);
        account.markPendingValidation(NOW);

        assertEquals(BrokerConnectionStatus.PENDING_VALIDATION, account.connectionStatus());
    }

    @Test
    void validTransitionReauthenticationRequiredToPendingValidation() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.markReauthenticationRequired(NOW);
        account.markPendingValidation(NOW);

        assertEquals(BrokerConnectionStatus.PENDING_VALIDATION, account.connectionStatus());
    }

    @Test
    void validTransitionDisconnectedToPendingValidation() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.disconnect(NOW);
        account.markPendingValidation(NOW);

        assertEquals(BrokerConnectionStatus.PENDING_VALIDATION, account.connectionStatus());
    }

    @Test
    void validTransitionDisconnectedToRevoked() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.disconnect(NOW);
        account.markRevoked(NOW);

        assertEquals(BrokerConnectionStatus.REVOKED, account.connectionStatus());
    }

    @Test
    void invalidTransitionCreatedToConnectedThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW));
    }

    @Test
    void invalidTransitionCreatedToInvalidCredentialsThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markInvalidCredentials(NOW));
    }

    @Test
    void invalidTransitionCreatedToInsufficientPermissionsThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markInsufficientPermissions(NOW));
    }

    @Test
    void invalidTransitionCreatedToTemporarilyUnavailableThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markTemporarilyUnavailable(NOW));
    }

    @Test
    void invalidTransitionCreatedToReauthenticationRequiredThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markReauthenticationRequired(NOW));
    }

    @Test
    void invalidTransitionCreatedToDisconnectedThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.disconnect(NOW));
    }

    @Test
    void invalidTransitionCreatedToRevokedThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markRevoked(NOW));
    }

    @Test
    void invalidTransitionPendingValidationToReauthenticationRequiredThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markReauthenticationRequired(NOW));
    }

    @Test
    void invalidTransitionPendingValidationToDisconnectedThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.disconnect(NOW));
    }

    @Test
    void invalidTransitionPendingValidationToRevokedThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markRevoked(NOW));
    }

    @Test
    void invalidTransitionConnectedToInvalidCredentialsThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markInvalidCredentials(NOW));
    }

    @Test
    void invalidTransitionConnectedToInsufficientPermissionsThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markInsufficientPermissions(NOW));
    }

    @Test
    void invalidTransitionInvalidCredentialsToConnectedThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markInvalidCredentials(NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW));
    }

    @Test
    void invalidTransitionInsufficientPermissionsToConnectedThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markInsufficientPermissions(NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW));
    }

    @Test
    void invalidTransitionRevokedToAnyThrows() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.markRevoked(NOW);

        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markPendingValidation(NOW));
        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW));
        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.disconnect(NOW));
    }

    @Test
    void markConnectedRequiresNonNullCredentialReference() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);

        assertThrows(NullPointerException.class,
                () -> account.markConnected(null, null, NOW));
    }

    @Test
    void markConnectedWithNullExternalAccountIdSucceeds() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);

        assertEquals(BrokerConnectionStatus.CONNECTED, account.connectionStatus());
        assertNull(account.externalAccountId());
    }

    @Test
    void markConnectedUpdatesExternalAccountId() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), "ext-456", NOW);

        assertEquals("ext-456", account.externalAccountId());
    }

    @Test
    void rotationPreservesIdentity() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        UUID id = account.id();
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(UUID.randomUUID()), null, NOW);

        assertEquals(id, account.id());
    }

    @Test
    void rotationUpdatesCredentialReference() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        CredentialReference first = new CredentialReference(CREDENTIAL_REF);
        account.markConnected(first, null, NOW);
        CredentialReference second = new CredentialReference(UUID.randomUUID());
        account.markPendingValidation(NOW);
        account.markConnected(second, null, NOW);

        assertNotEquals(first, account.credentialReference());
        assertEquals(second.value(), account.credentialReference().value());
    }

    @Test
    void markRevokedClearsCredentialReference() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);
        account.markRevoked(NOW);

        assertNull(account.credentialReference());
    }

    @Test
    void ownerIdAndProviderAreImmutable() {
        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", NOW);
        account.markPendingValidation(NOW);
        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, NOW);

        assertEquals(OWNER, account.ownerId());
        assertEquals(BrokerProvider.KRAKEN, account.provider());
    }

    @Test
    void updatedAtUpdatesOnEachTransition() {
        Instant t1 = NOW;
        Instant t2 = NOW.plusSeconds(1);
        Instant t3 = NOW.plusSeconds(2);

        BrokerAccount account = BrokerAccount.create(OWNER, BrokerProvider.KRAKEN, ExecutionMode.LIVE, "Test", t1);
        assertEquals(t1, account.updatedAt());

        account.markPendingValidation(t2);
        assertEquals(t2, account.updatedAt());

        account.markConnected(new CredentialReference(CREDENTIAL_REF), null, t3);
        assertEquals(t3, account.updatedAt());
    }
}