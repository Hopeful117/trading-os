package com.hope.trading.trading_core.brokeraccount.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrokerAccountTest {
    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    @Test
    void createsAccountWithoutCredentialAndValidatesRequiredValues() {
        BrokerAccount account = BrokerAccount.create(UUID.randomUUID(), BrokerProvider.KRAKEN, " Main Kraken ", NOW);
        assertEquals(BrokerConnectionStatus.CREATED, account.connectionStatus());
        assertEquals("Main Kraken", account.displayName());
        assertThrows(NullPointerException.class,
                () -> BrokerAccount.create(null, BrokerProvider.KRAKEN, "name", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> BrokerAccount.create(UUID.randomUUID(), BrokerProvider.KRAKEN, " ", NOW));
    }

    @Test
    void enforcesLifecycleAndConnectedReference() {
        BrokerAccount account = BrokerAccount.create(UUID.randomUUID(), BrokerProvider.KRAKEN, "Kraken", NOW);
        assertThrows(InvalidBrokerConnectionTransitionException.class,
                () -> account.markConnected(new CredentialReference(UUID.randomUUID()), null, NOW));
        account.markPendingValidation(NOW.plusSeconds(1));
        assertThrows(NullPointerException.class, () -> account.markConnected(null, null, NOW.plusSeconds(2)));
        account.markConnected(new CredentialReference(UUID.randomUUID()), "external", NOW.plusSeconds(2));
        assertEquals(BrokerConnectionStatus.CONNECTED, account.connectionStatus());
    }

    @Test
    void rotationKeepsIdentityAndRevokeDiffersFromDisconnect() {
        UUID owner = UUID.randomUUID();
        BrokerAccount account = BrokerAccount.create(owner, BrokerProvider.KRAKEN, "Kraken", NOW);
        UUID id = account.id();
        account.markPendingValidation(NOW);
        CredentialReference first = new CredentialReference(UUID.randomUUID());
        account.markConnected(first, null, NOW);
        account.markPendingValidation(NOW);
        CredentialReference second = new CredentialReference(UUID.randomUUID());
        account.markConnected(second, null, NOW);
        assertEquals(id, account.id());
        assertNotEquals(first, account.credentialReference());
        account.disconnect(NOW);
        assertEquals(BrokerConnectionStatus.DISCONNECTED, account.connectionStatus());
        account.markRevoked(NOW);
        assertEquals(BrokerConnectionStatus.REVOKED, account.connectionStatus());
        assertThrows(InvalidBrokerConnectionTransitionException.class, () -> account.markPendingValidation(NOW));
    }
}
