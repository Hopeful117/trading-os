package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaAnalysisTradePlanGenerationRepository
        extends JpaRepository<JpaAnalysisTradePlanGenerationEntity, UUID> {
    Optional<JpaAnalysisTradePlanGenerationEntity>
    findByAnalysisExecutionIdAndActorIdAndAccountIdAndIdempotencyKey(
            UUID analysisExecutionId, UUID actorId, UUID accountId, String idempotencyKey);
}
