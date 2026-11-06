package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecycle;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface HistoricalReconstructionLifecycleRepository {
    HistoricalReconstructionLifecycle save(HistoricalReconstructionLifecycle lifecycle);

    boolean saveOwned(HistoricalReconstructionLifecycle lifecycle, UUID leaseId);

    boolean saveIfAbsent(HistoricalReconstructionLifecycle lifecycle);

    boolean claimIfStale(UUID executionId, UUID leaseId, Instant staleBefore, Instant claimedAt);

    boolean isOwned(UUID executionId, UUID leaseId);

    Optional<HistoricalReconstructionLifecycle> findByExecutionId(UUID executionId);
}
