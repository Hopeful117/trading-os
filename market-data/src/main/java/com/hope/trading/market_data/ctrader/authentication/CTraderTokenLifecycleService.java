package com.hope.trading.market_data.ctrader.authentication;

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
public class CTraderTokenLifecycleService {
    private final CTraderTokenStore store;
    private final Clock clock;
    private final CTraderOAuthTokenClient refreshClient;
    private final CTraderEnvironment configuredEnvironment;
    private final long configuredExternalAccountId;
    private final ConcurrentHashMap<UUID, Object> refreshLocks = new ConcurrentHashMap<>();

    public CTraderTokenLifecycleService(CTraderTokenStore store, Clock clock) {
        this(store, clock, null);
    }

    public CTraderTokenLifecycleService(CTraderTokenStore store, Clock clock, CTraderOAuthTokenClient refreshClient) {
        this(store, clock, refreshClient, null);
    }

    public CTraderTokenLifecycleService(CTraderTokenStore store, Clock clock, CTraderOAuthTokenClient refreshClient,
                                       CTraderMarketDataProperties properties) {
        this(store, clock, refreshClient, properties, null);
    }

    @Autowired
    public CTraderTokenLifecycleService(CTraderTokenStore store, Clock clock, CTraderOAuthTokenClient refreshClient,
                                       CTraderMarketDataProperties properties,
                                       CTraderMarketDataTokenProperties tokenProperties) {
        this.store = store;
        this.clock = clock;
        this.refreshClient = refreshClient;
        this.configuredEnvironment = tokenProperties != null ? tokenProperties.environment()
                : properties == null ? null : properties.environment();
        this.configuredExternalAccountId = tokenProperties != null ? tokenProperties.externalAccountId() : 0;
    }

    @Transactional
    public CTraderTokenStore.StoredCTraderToken requireUsableToken() {
        return requireUsableToken(false);
    }

    @Transactional
    public CTraderTokenStore.StoredCTraderToken requireUsableToken(boolean refreshExpired) {
        CTraderTokenStore.StoredCTraderToken token = store.findActive()
                .orElseThrow(() -> new CTraderAuthenticationRequiredException());
        if (!matchesConfiguration(token.record())) {
            token.close();
            throw new CTraderAuthenticationRequiredException();
        }
        if (token.record().usableAt(clock.instant())) return token;
        if (!refreshExpired || refreshClient == null) {
            token.close();
            throw new CTraderAuthenticationRequiredException();
        }
        UUID id = token.record().id();
        synchronized (refreshLocks.computeIfAbsent(id, ignored -> new Object())) {
            try (token) {
                CTraderTokenStore.StoredCTraderToken current = store.findActive()
                        .orElseThrow(CTraderAuthenticationRequiredException::new);
                if (!matchesConfiguration(current.record())) {
                    current.close();
                    throw new CTraderAuthenticationRequiredException();
                }
                if (current.record().usableAt(clock.instant())) {
                    return current;
                }
                char[] refresh = current.material().copyRefreshToken();
                try (current; CTraderTokenMaterial replacement = refreshClient.refresh(refresh)) {
                    return store.rotate(current.record().id(), current.record().credentialVersion(), replacement);
                } catch (CTraderTokenRefreshException exception) {
                    if (exception.confirmedInvalid()) store.markInvalid(id);
                    throw new CTraderAuthenticationRequiredException();
                } finally {
                    java.util.Arrays.fill(refresh, '\0');
                }
            }
        }
    }

    @Transactional
    public CTraderTokenRecord provision(CTraderEnvironment environment, long externalAccountId,
                                        CTraderTokenMaterial material) {
        if ((configuredEnvironment != null && configuredEnvironment != environment)
                || (configuredExternalAccountId > 0 && configuredExternalAccountId != externalAccountId)) {
            throw new CTraderAuthenticationRequiredException();
        }
        var existing = store.findActive();
        if (existing.isPresent()) {
            try (material; var active = existing.get()) {
                if (active.record().environment() != environment || active.record().externalAccountId() != externalAccountId) {
                    throw new IllegalStateException("Active cTrader token source does not match configuration");
                }
                if (active.record().usableAt(clock.instant())) return active.record();
                char[] access = material.copyAccessToken();
                char[] refresh = material.copyRefreshToken();
                try (CTraderTokenMaterial replacement = new CTraderTokenMaterial(access, refresh, material.expiresAt())) {
                    try (var rotated = store.rotate(active.record().id(), active.record().credentialVersion(), replacement)) {
                        return rotated.record();
                    }
                } finally {
                    java.util.Arrays.fill(access, '\0');
                    java.util.Arrays.fill(refresh, '\0');
                }
            }
        }
        try (material) {
            try (var created = store.create(environment, externalAccountId, material, 1)) {
                return created.record();
            }
        }
    }

    @Transactional
    public CTraderTokenRecord rotate(UUID id, long expectedCredentialVersion,
                                     CTraderTokenMaterial replacement) {
        try (replacement) {
            try (var rotated = store.rotate(id, expectedCredentialVersion, replacement)) {
                return rotated.record();
            }
        }
    }

    @Transactional
    public void invalidate(UUID id) { store.markInvalid(id); }

    @Transactional
    public void revoke(UUID id) { store.revoke(id); }

    public Instant now() { return clock.instant(); }

    private boolean matchesConfiguration(CTraderTokenRecord record) {
        return (configuredEnvironment == null || record.environment() == configuredEnvironment)
                && (configuredExternalAccountId <= 0 || record.externalAccountId() == configuredExternalAccountId);
    }
}
