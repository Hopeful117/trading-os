package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class JpaAnalysisTradePlanGenerationClaimService {
    private final JpaAnalysisTradePlanGenerationRepository generations;

    public JpaAnalysisTradePlanGenerationClaimService(
            JpaAnalysisTradePlanGenerationRepository generations) {
        this.generations = generations;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JpaAnalysisTradePlanGenerationEntity create(
            UUID analysisId, UUID actorId, UUID accountId, UUID contextId,
            long contextVersion, String idempotencyKey, Instant createdAt) {
        return generations.saveAndFlush(JpaAnalysisTradePlanGenerationEntity.running(
                analysisId, actorId, accountId, contextId, contextVersion,
                idempotencyKey, createdAt));
    }
}
