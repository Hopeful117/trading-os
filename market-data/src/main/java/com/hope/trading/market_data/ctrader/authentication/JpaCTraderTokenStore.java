package com.hope.trading.market_data.ctrader.authentication;

import com.hope.trading.security.crypto.CryptoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
public class JpaCTraderTokenStore implements CTraderTokenStore {
    private final CTraderMarketDataTokenRepository repository;
    private final CTraderTokenCryptoAdapter crypto;
    private final Clock clock;

    public JpaCTraderTokenStore(CTraderMarketDataTokenRepository repository,
                                CTraderTokenCryptoAdapter crypto, Clock clock) {
        this.repository = repository; this.crypto = crypto; this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredCTraderToken> findActive() {
        return repository.findFirstByStatus(CTraderTokenStatus.ACTIVE).map(this::stored);
    }

    @Override
    @Transactional
    public StoredCTraderToken create(CTraderEnvironment environment, long accountId,
                                     CTraderTokenMaterial material, long version) {
        if (repository.findFirstByStatus(CTraderTokenStatus.ACTIVE).isPresent()) {
            throw new IllegalStateException("An active cTrader Market Data token already exists");
        }
        CTraderMarketDataTokenEntity entity = newEntity(environment, accountId, material, version);
        return stored(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public StoredCTraderToken rotate(UUID id, long expectedVersion, CTraderTokenMaterial replacement) {
        CTraderMarketDataTokenEntity entity = repository.findById(id)
                .filter(candidate -> candidate.status() == CTraderTokenStatus.ACTIVE)
                .orElseThrow(CTraderAuthenticationRequiredException::new);
        if (entity.credentialVersion() != expectedVersion) throw new IllegalStateException("Token changed concurrently");
        entity.rotate(encryptAccess(replacement), encryptRefresh(replacement),
                replacement.expiresAt(), clock.instant());
        try {
            return stored(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException | org.springframework.orm.ObjectOptimisticLockingFailureException exception) {
            throw new IllegalStateException("Token changed concurrently", exception);
        }
    }

    @Override @Transactional public void markInvalid(UUID id) { updateStatus(id, entity -> entity.invalidate(clock.instant())); }
    @Override @Transactional public void revoke(UUID id) { updateStatus(id, entity -> entity.revoke(clock.instant())); }

    private void updateStatus(UUID id, java.util.function.Consumer<CTraderMarketDataTokenEntity> action) {
        repository.findById(id).ifPresent(action);
    }

    private CTraderMarketDataTokenEntity newEntity(CTraderEnvironment environment, long accountId,
                                                   CTraderTokenMaterial material, long version) {
        return CTraderMarketDataTokenEntity.active(UUID.randomUUID(), environment, accountId,
                encryptAccess(material), encryptRefresh(material),
                material.expiresAt(), version, clock.instant());
    }

    private CTraderTokenCryptoAdapter.EncryptedToken encryptAccess(CTraderTokenMaterial material) {
        char[] value = material.copyAccessToken();
        try { return crypto.encrypt(value); } finally { java.util.Arrays.fill(value, '\0'); }
    }

    private CTraderTokenCryptoAdapter.EncryptedToken encryptRefresh(CTraderTokenMaterial material) {
        char[] value = material.copyRefreshToken();
        try { return crypto.encrypt(value); } finally { java.util.Arrays.fill(value, '\0'); }
    }

    private StoredCTraderToken stored(CTraderMarketDataTokenEntity entity) {
        try {
            return new StoredCTraderToken(new CTraderTokenRecord(entity.id(), entity.environment(),
                    entity.externalAccountId(), entity.credentialVersion(), entity.expiresAt(),
                    entity.updatedAt(), entity.status()), new CTraderTokenMaterial(
                    crypto.decrypt(entity.access()), crypto.decrypt(entity.refresh()), entity.expiresAt()));
        } catch (CryptoException exception) {
            throw new IllegalStateException("Unable to resolve cTrader Market Data token", exception);
        }
    }
}
