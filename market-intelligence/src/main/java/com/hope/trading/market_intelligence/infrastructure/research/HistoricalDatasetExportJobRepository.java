package com.hope.trading.market_intelligence.infrastructure.research;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HistoricalDatasetExportJobRepository extends JpaRepository<HistoricalDatasetExportJobEntity, UUID> {
    Optional<HistoricalDatasetExportJobEntity> findByAgentIdAndIdempotencyKey(UUID agentId, String idempotencyKey);
    Optional<HistoricalDatasetExportJobEntity> findByIdAndAgentId(UUID id, UUID agentId);
}
