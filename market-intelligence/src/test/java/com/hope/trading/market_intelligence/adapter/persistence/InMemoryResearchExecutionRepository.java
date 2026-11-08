package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.ResearchExecutionRepository;
import com.hope.trading.market_intelligence.domain.research.ResearchExecution;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryResearchExecutionRepository implements ResearchExecutionRepository {
    private final ConcurrentMap<UUID, ResearchExecution> values = new ConcurrentHashMap<>();

    @Override
    public ResearchExecution save(ResearchExecution execution) {
        values.put(execution.researchId(), execution);
        return execution;
    }

    @Override
    public Optional<ResearchExecution> findById(UUID researchId) {
        return Optional.ofNullable(values.get(researchId));
    }

    @Override
    public Optional<ResearchExecution> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey) {
        return values.values().stream()
                .filter(value -> value.actorId().equals(actorId))
                .filter(value -> value.idempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    @Override
    public List<ResearchExecution> findByStatus(ResearchExecutionStatus status) {
        return values.values().stream().filter(value -> value.status() == status).toList();
    }

    @Override
    public synchronized boolean saveIfStatus(
            ResearchExecution execution, ResearchExecutionStatus expectedStatus) {
        return saveIfStatusAndAttempt(execution, expectedStatus, execution.attemptCount());
    }

    @Override
    public synchronized boolean saveIfStatusAndAttempt(
            ResearchExecution execution, ResearchExecutionStatus expectedStatus, int expectedAttempt) {
        ResearchExecution current = values.get(execution.researchId());
        if (current == null || current.status() != expectedStatus || current.attemptCount() != expectedAttempt
                || execution.attemptCount() != expectedAttempt) {
            return false;
        }
        values.put(execution.researchId(), execution);
        return true;
    }

    @Override
    public synchronized boolean requeueIfStale(ResearchExecution execution,
                                               java.time.Instant staleBefore, java.time.Instant now) {
        ResearchExecution current = values.get(execution.researchId());
        if (current == null || current.status() != ResearchExecutionStatus.RUNNING
                || current.attemptCount() != execution.attemptCount()
                || !current.updatedAt().equals(execution.updatedAt())
                || !current.updatedAt().isBefore(staleBefore)) {
            return false;
        }
        values.put(execution.researchId(), current.requeue(now));
        return true;
    }

    @Override
    public synchronized Optional<ResearchExecution> claimRequested(UUID researchId, java.time.Instant now) {
        return findById(researchId)
                .filter(value -> value.status() == ResearchExecutionStatus.REQUESTED)
                .map(value -> save(value.running(now)));
    }
}
