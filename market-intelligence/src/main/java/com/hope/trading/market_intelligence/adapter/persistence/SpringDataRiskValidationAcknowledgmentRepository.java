package com.hope.trading.market_intelligence.adapter.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRiskValidationAcknowledgmentRepository
        extends JpaRepository<JpaRiskValidationAcknowledgmentEntity, UUID> {
    Optional<JpaRiskValidationAcknowledgmentEntity>
            findByTradePlanIdAndAcceptedTradePlanVersion(UUID tradePlanId, long version);
    Optional<JpaRiskValidationAcknowledgmentEntity> findByEvaluationId(UUID evaluationId);
}
