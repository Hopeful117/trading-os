package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.ManualTradePlanIdempotencyRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaManualTradePlanIdempotencyRepository
        implements ManualTradePlanIdempotencyRepository {
    private final SpringDataManualTradePlanIdempotencyRepository repository;

    public JpaManualTradePlanIdempotencyRepository(
            SpringDataManualTradePlanIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Entry> findByActorIdAndKey(UUID actorId, String key) {
        return repository.findByActorIdAndKey(actorId, key).map(this::toDomain);
    }

    @Override
    @Transactional
    public Entry save(Entry entry) {
        JpaManualTradePlanIdempotencyEntity entity = new JpaManualTradePlanIdempotencyEntity();
        entity.id = UUID.randomUUID();
        entity.actorId = entry.actorId();
        entity.key = entry.key();
        entity.fingerprint = entry.fingerprint();
        entity.tradePlanId = entry.tradePlanId();
        entity.tradePlanVersion = entry.tradePlanVersion();
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("Manual Trade Plan idempotency key is already used", exception);
        }
        return entry;
    }

    private Entry toDomain(JpaManualTradePlanIdempotencyEntity entity) {
        return new Entry(entity.actorId, entity.key, entity.fingerprint,
                entity.tradePlanId, entity.tradePlanVersion);
    }
}
