package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.research.ResearchExecution;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus;
import java.util.List;

public interface ResearchExecutionRepository {
    ResearchExecution save(ResearchExecution execution);

    Optional<ResearchExecution> findById(UUID researchId);

    Optional<ResearchExecution> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey);

    List<ResearchExecution> findByStatus(ResearchExecutionStatus status);

    boolean saveIfStatus(ResearchExecution execution, ResearchExecutionStatus expectedStatus);

    boolean saveIfStatusAndAttempt(ResearchExecution execution, ResearchExecutionStatus expectedStatus,
                                   int expectedAttempt);

    boolean requeueIfStale(ResearchExecution execution, Instant staleBefore, Instant now);

    default Optional<ResearchExecution> claimRequested(UUID researchId, Instant now) {
        return findById(researchId)
                .filter(value -> value.status() == ResearchExecutionStatus.REQUESTED)
                .map(value -> save(value.running(now)));
    }
}
