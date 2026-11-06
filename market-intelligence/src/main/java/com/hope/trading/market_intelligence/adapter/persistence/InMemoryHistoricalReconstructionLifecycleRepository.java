package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.HistoricalReconstructionLifecycleRepository;
import com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecycle;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryHistoricalReconstructionLifecycleRepository
        implements HistoricalReconstructionLifecycleRepository {
    private final ConcurrentMap<UUID, HistoricalReconstructionLifecycle> lifecycles = new ConcurrentHashMap<>();

    @Override
    public HistoricalReconstructionLifecycle save(HistoricalReconstructionLifecycle lifecycle) {
        lifecycles.put(lifecycle.executionId(), lifecycle);
        return lifecycle;
    }

    @Override
    public boolean saveOwned(HistoricalReconstructionLifecycle lifecycle, UUID leaseId) {
        java.util.concurrent.atomic.AtomicBoolean saved = new java.util.concurrent.atomic.AtomicBoolean(false);
        lifecycles.computeIfPresent(lifecycle.executionId(), (ignored, current) -> {
            if (!current.leaseId().equals(leaseId)) return current;
            saved.set(true);
            return lifecycle;
        });
        return saved.get();
    }

    @Override
    public boolean saveIfAbsent(HistoricalReconstructionLifecycle lifecycle) {
        return lifecycles.putIfAbsent(lifecycle.executionId(), lifecycle) == null;
    }

    @Override
    public boolean claimIfStale(UUID executionId, UUID leaseId, java.time.Instant staleBefore,
                                java.time.Instant claimedAt) {
        java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean(false);
        lifecycles.computeIfPresent(executionId, (ignored, current) -> {
            if (current.status() != com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecycleStatus.RUNNING
                    || current.updatedAt().isAfter(staleBefore)) return current;
            claimed.set(true);
            return new com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecycle(
                    current.executionId(), leaseId, current.status(), current.phase(), current.failureCode(),
                    current.failureReason(), current.snapshotFingerprint(), current.outcomeFingerprint(),
                    current.outputDirectory(), current.startedAt(), claimedAt, current.completedAt());
        });
        return claimed.get();
    }

    @Override
    public Optional<HistoricalReconstructionLifecycle> findByExecutionId(UUID executionId) {
        return Optional.ofNullable(lifecycles.get(executionId));
    }
}
