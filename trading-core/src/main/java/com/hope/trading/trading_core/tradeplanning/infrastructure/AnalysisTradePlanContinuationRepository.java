package com.hope.trading.trading_core.tradeplanning.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnalysisTradePlanContinuationRepository
        extends JpaRepository<AnalysisTradePlanContinuationEntity, UUID> {
    Optional<AnalysisTradePlanContinuationEntity>
    findByAnalysisExecutionIdAndActorIdAndAccountIdAndIdempotencyKey(
            UUID analysisExecutionId, UUID actorId, UUID accountId, String idempotencyKey);
}
