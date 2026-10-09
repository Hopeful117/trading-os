package com.hope.trading.trading_core.tradeplanning.infrastructure;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AnalysisTradePlanContinuationClaimService {
    private final AnalysisTradePlanContinuationRepository continuations;

    public AnalysisTradePlanContinuationClaimService(
            AnalysisTradePlanContinuationRepository continuations) {
        this.continuations = continuations;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisTradePlanContinuationEntity create(
            UUID analysisId, UUID actorId, UUID accountId, String key,
            UUID contextId, long contextVersion, Instant capturedAt,
            UUID profileId, long profileVersion, Instant createdAt) {
        return continuations.saveAndFlush(AnalysisTradePlanContinuationEntity.pending(
                analysisId, actorId, accountId, key, contextId, contextVersion,
                capturedAt, profileId, profileVersion, createdAt));
    }
}
