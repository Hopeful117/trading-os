package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for technical execution history.
 * Storage technology and retention are intentionally outside the domain.
 */
public interface AnalysisExecutionRepository {
    AnalysisExecution save(AnalysisExecution execution);

    Optional<AnalysisExecution> findById(UUID executionId);

    Optional<AnalysisExecution> findReusable(IdempotencyKey key, Instant now);
}
