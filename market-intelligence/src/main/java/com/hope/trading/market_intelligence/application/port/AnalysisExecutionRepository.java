package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for technical execution history.
 * Storage technology and retention are intentionally outside the domain.
 */
public interface AnalysisExecutionRepository {
    AnalysisExecution save(AnalysisExecution execution);

    Optional<AnalysisExecution> findById(UUID executionId);

    List<AnalysisExecution> findAllById(Collection<UUID> executionIds);

    Optional<AnalysisExecution> findLatestByMarketId(UUID marketId);

    Optional<AnalysisExecution> findReusable(IdempotencyKey key, Instant now);

    Optional<AnalysisExecution> findByIdempotencyKey(IdempotencyKey key);

    List<AnalysisExecution> findByResearchScope(
            Collection<UUID> marketIds,
            Instant startCutoffInclusive,
            Instant endCutoffExclusive,
            EvidenceAcquisitionPolicy evidencePolicy
    );

    boolean transitionStatus(
            UUID executionId,
            AnalysisExecutionStatus expected,
            AnalysisExecutionStatus target,
            Instant updatedAt
    );
}
