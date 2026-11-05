package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataResearchExecutionRepository extends JpaRepository<JpaResearchExecutionEntity, UUID> {
    Optional<JpaResearchExecutionEntity> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey);
}
