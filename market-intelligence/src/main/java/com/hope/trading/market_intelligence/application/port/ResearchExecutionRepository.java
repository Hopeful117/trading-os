package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.research.ResearchExecution;

import java.util.Optional;
import java.util.UUID;

public interface ResearchExecutionRepository {
    ResearchExecution save(ResearchExecution execution);

    Optional<ResearchExecution> findById(UUID researchId);

    Optional<ResearchExecution> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey);
}
