package com.hope.trading.market_data.ctrader.authentication;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class CTraderTokenLifecycleServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void rotationReplacesAccessAndRefreshTogetherAndAdvancesVersion() {
        InMemoryStore store = new InMemoryStore();
        CTraderTokenLifecycleService service = new CTraderTokenLifecycleService(store, fixedClock());
        CTraderTokenRecord first = service.provision(CTraderEnvironment.DEMO, 123L,
                material("access-1", "refresh-1", NOW.plusSeconds(600)));

        CTraderTokenRecord rotated = service.rotate(first.id(), first.credentialVersion(),
                material("access-2", "refresh-2", NOW.plusSeconds(1200)));

        assertEquals(2, rotated.credentialVersion());
        assertArrayEquals("access-2".toCharArray(), store.access.toCharArray());
        assertArrayEquals("refresh-2".toCharArray(), store.refresh.toCharArray());
        assertEquals(CTraderTokenStatus.ACTIVE, rotated.status());
    }

    @Test
    void expiredOrInvalidTokenCannotBeUsed() {
        InMemoryStore store = new InMemoryStore();
        CTraderTokenLifecycleService service = new CTraderTokenLifecycleService(store, fixedClock());
        CTraderTokenRecord first = service.provision(CTraderEnvironment.LIVE, 456L,
                material("access", "refresh", NOW.minusSeconds(1)));

        assertThrows(CTraderAuthenticationRequiredException.class, service::requireUsableToken);
        service.invalidate(first.id());
        assertEquals(CTraderTokenStatus.INVALID, store.record.status());
    }

    @Test
    void staleRotationVersionIsRejectedWithoutChangingStoredMaterial() {
        InMemoryStore store = new InMemoryStore();
        CTraderTokenLifecycleService service = new CTraderTokenLifecycleService(store, fixedClock());
        CTraderTokenRecord first = service.provision(CTraderEnvironment.DEMO, 123L,
                material("access-1", "refresh-1", NOW.plusSeconds(600)));

        assertThrows(IllegalStateException.class, () -> service.rotate(first.id(), 99,
                material("access-2", "refresh-2", NOW.plusSeconds(1200))));
        assertEquals("access-1", store.access);
        assertEquals("refresh-1", store.refresh);
    }

    @Test
    void expiredTokenRefreshIsSerializedThroughAtomicRotation() {
        InMemoryStore store = new InMemoryStore();
        CTraderOAuthTokenClient refreshClient = new CTraderOAuthTokenClient(
                new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "client", "secret",
                        "https://token.example", "wss://fixture", java.time.Duration.ofSeconds(1),
                        java.util.Map.of(), 2, 4), new ObjectMapper(), fixedClock(),
                (endpoint, form, timeout) -> new CTraderOAuthTokenClient.TokenHttpResponse(200,
                        "{\"access_token\":\"access-2\",\"refresh_token\":\"refresh-2\",\"expires_in\":600}"));
        CTraderTokenLifecycleService service = new CTraderTokenLifecycleService(store, fixedClock(), refreshClient);
        service.provision(CTraderEnvironment.DEMO, 123L, material("access-1", "refresh-1", NOW.minusSeconds(1)));

        try (CTraderTokenStore.StoredCTraderToken refreshed = service.requireUsableToken(true)) {
            assertArrayEquals("access-2".toCharArray(), refreshed.material().copyAccessToken());
            assertArrayEquals("refresh-2".toCharArray(), refreshed.material().copyRefreshToken());
            assertEquals(2, refreshed.record().credentialVersion());
        }
    }

    @Test
    void bootstrapProvisionIsIdempotentForAnExistingUsableToken() {
        InMemoryStore store = new InMemoryStore();
        CTraderTokenLifecycleService service = new CTraderTokenLifecycleService(store, fixedClock());
        CTraderTokenRecord first = service.provision(CTraderEnvironment.DEMO, 123L,
                material("access-1", "refresh-1", NOW.plusSeconds(600)));

        CTraderTokenRecord again = service.provision(CTraderEnvironment.DEMO, 123L,
                material("bootstrap-access", "bootstrap-refresh", NOW.plusSeconds(600)));

        assertEquals(first.id(), again.id());
        assertEquals("access-1", store.access);
        assertEquals(1, again.credentialVersion());
    }

    @Test
    void transientRefreshFailurePreservesActiveTokenMaterial() {
        InMemoryStore store = new InMemoryStore();
        CTraderOAuthTokenClient refreshClient = new CTraderOAuthTokenClient(
                new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "client", "secret",
                        "https://token.example", "wss://fixture", java.time.Duration.ofSeconds(1),
                        java.util.Map.of(), 2, 4), new ObjectMapper(), fixedClock(),
                (endpoint, form, timeout) -> { throw new java.io.IOException("timeout"); });
        CTraderTokenLifecycleService service = new CTraderTokenLifecycleService(store, fixedClock(), refreshClient);
        service.provision(CTraderEnvironment.DEMO, 123L, material("access-1", "refresh-1", NOW.minusSeconds(1)));

        assertThrows(CTraderAuthenticationRequiredException.class, () -> service.requireUsableToken(true));
        assertEquals(CTraderTokenStatus.ACTIVE, store.record.status());
        assertEquals("refresh-1", store.refresh);
    }

    @Test
    void storedTokenEnvironmentMustMatchConfiguredEnvironment() {
        InMemoryStore store = new InMemoryStore();
        CTraderMarketDataProperties properties = new CTraderMarketDataProperties(CTraderEnvironment.DEMO,
                "client", "secret", "wss://fixture", java.time.Duration.ofSeconds(1), java.util.Map.of());
        new CTraderTokenLifecycleService(store, fixedClock()).provision(CTraderEnvironment.LIVE, 123L,
                material("access", "refresh", NOW.plusSeconds(600)));
        CTraderTokenLifecycleService service = new CTraderTokenLifecycleService(store, fixedClock(), null, properties);

        assertThrows(CTraderAuthenticationRequiredException.class, service::requireUsableToken);
    }

    private static CTraderTokenMaterial material(String access, String refresh, Instant expiry) {
        return new CTraderTokenMaterial(access.toCharArray(), refresh.toCharArray(), expiry);
    }

    private static Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }

    private static final class InMemoryStore implements CTraderTokenStore {
        private UUID id;
        private String access;
        private String refresh;
        private CTraderTokenRecord record;

        @Override public Optional<StoredCTraderToken> findActive() {
            return record != null && record.status() == CTraderTokenStatus.ACTIVE
                    ? Optional.of(stored()) : Optional.empty();
        }

        @Override public StoredCTraderToken create(CTraderEnvironment environment, long account,
                                                   CTraderTokenMaterial material, long version) {
            id = UUID.randomUUID();
            access = new String(material.copyAccessToken());
            refresh = new String(material.copyRefreshToken());
            record = new CTraderTokenRecord(id, environment, account, version, material.expiresAt(), NOW,
                    CTraderTokenStatus.ACTIVE);
            return stored();
        }

        @Override public StoredCTraderToken rotate(UUID tokenId, long expected, CTraderTokenMaterial replacement) {
            if (!id.equals(tokenId) || record.credentialVersion() != expected) throw new IllegalStateException();
            access = new String(replacement.copyAccessToken());
            refresh = new String(replacement.copyRefreshToken());
            record = new CTraderTokenRecord(id, record.environment(), record.externalAccountId(), expected + 1,
                    replacement.expiresAt(), NOW, CTraderTokenStatus.ACTIVE);
            return stored();
        }

        @Override public void markInvalid(UUID tokenId) {
            record = new CTraderTokenRecord(id, record.environment(), record.externalAccountId(),
                    record.credentialVersion(), record.expiresAt(), record.updatedAt(), CTraderTokenStatus.INVALID);
        }

        @Override public void revoke(UUID tokenId) { markInvalid(tokenId); }

        private StoredCTraderToken stored() {
            return new StoredCTraderToken(record, material(access, refresh, record.expiresAt()));
        }
    }
}
