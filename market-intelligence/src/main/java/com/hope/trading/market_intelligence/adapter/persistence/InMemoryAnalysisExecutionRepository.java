package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * V1 adapter used until execution retention is decided. It deliberately
 * implements the repository port without claiming durable persistence.
 */
public class InMemoryAnalysisExecutionRepository implements AnalysisExecutionRepository {
    private final ConcurrentMap<UUID, AnalysisExecution> executions = new ConcurrentHashMap<>();

    @Override
    public AnalysisExecution save(AnalysisExecution execution) {
        executions.put(execution.executionId(), execution);
        return execution;
    }

    @Override
    public Optional<AnalysisExecution> findById(UUID executionId) {
        return Optional.ofNullable(executions.get(executionId));
    }

    @Override
    public Optional<AnalysisExecution> findReusable(IdempotencyKey key, Instant now) {
        return executions.values().stream()
                .filter(execution -> execution.idempotencyKey().equals(key))
                .filter(execution -> now.isBefore(execution.expiresAt()))
                .filter(execution -> execution.status() != AnalysisExecutionStatus.FAILED)
                .filter(execution -> execution.status() != AnalysisExecutionStatus.CANCELLED)
                .filter(execution -> execution.status() != AnalysisExecutionStatus.EXPIRED)
                .findFirst();
    }

    @Override
    public boolean transitionStatus(
            UUID executionId,
            AnalysisExecutionStatus expected,
            AnalysisExecutionStatus target,
            Instant updatedAt
    ) {
        java.util.concurrent.atomic.AtomicBoolean transitioned =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        executions.computeIfPresent(executionId, (ignored, current) -> {
            if (current.status() != expected) {
                return current;
            }
            transitioned.set(true);
            return current.transitionTo(target, updatedAt);
        });
        return transitioned.get();
    }
}
