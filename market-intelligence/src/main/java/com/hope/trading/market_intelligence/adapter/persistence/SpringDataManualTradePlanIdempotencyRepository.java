package com.hope.trading.market_intelligence.adapter.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataManualTradePlanIdempotencyRepository
        extends JpaRepository<JpaManualTradePlanIdempotencyEntity, UUID> {
    Optional<JpaManualTradePlanIdempotencyEntity> findByActorIdAndKey(UUID actorId, String key);
}
