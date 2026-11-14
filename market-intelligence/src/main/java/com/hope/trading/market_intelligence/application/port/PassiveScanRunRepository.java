package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.passive.PassiveScanMarket;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanMarketStatus;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanRun;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PassiveScanRunRepository {
    PassiveScanRun saveRun(PassiveScanRun run);

    List<PassiveScanMarket> saveMarkets(List<PassiveScanMarket> markets);

    void saveAttempt(UUID attemptId, UUID scanMarketId, int attemptNumber,
                     UUID analysisExecutionId, String idempotencyKey, Instant createdAt);

    Optional<PassiveScanRun> findById(UUID runId);

    Optional<PassiveScanRun> findByIdempotencyKey(String idempotencyKey);

    List<PassiveScanMarket> findMarketsByRunId(UUID runId);

    Optional<PassiveScanMarket> findMarketById(UUID scanMarketId);

    Optional<PassiveScanMarket> findLatestMarketByMarketId(UUID marketId);

    long countActiveMarkets();

    List<PassiveScanMarket> findActiveMarkets();

    long countFailedAttempts(UUID marketId);

    boolean transitionRunStatus(UUID runId, PassiveScanRunStatus expected,
                                PassiveScanRunStatus target, String terminalSummary, Instant updatedAt);

    boolean transitionMarketStatus(UUID scanMarketId, PassiveScanMarketStatus expected,
                                   PassiveScanMarketStatus target, long expectedVersion, Instant updatedAt);

    boolean claimMarket(UUID scanMarketId, PassiveScanMarketStatus expected,
                        long expectedVersion, UUID leaseId, Instant leaseExpiresAt, Instant updatedAt);

    boolean recordMarketOutcome(UUID scanMarketId, PassiveScanMarketStatus expected,
                                 UUID leaseId, long expectedVersion, PassiveScanMarketStatus target,
                                 String failureCode, String failureReason, int attemptCount,
                                 Instant nextRetryAt, Instant updatedAt);

    boolean recoverExpiredMarket(UUID scanMarketId, PassiveScanMarketStatus expected,
                                 UUID leaseId, long expectedVersion, Instant updatedAt);
}
