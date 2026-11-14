package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

interface SpringDataPassiveScanMarketRepository extends JpaRepository<JpaPassiveScanMarketEntity, UUID> {
    List<JpaPassiveScanMarketEntity> findByRunIdOrderByOrdinalAsc(UUID runId);

    Optional<JpaPassiveScanMarketEntity> findFirstByMarketIdOrderByUpdatedAtDesc(UUID marketId);

    @Query("select count(e) from JpaPassiveScanMarketEntity e where e.status in ('REGISTERED','DISPATCH_REQUESTED','ACCEPTED','RUNNING')")
    long countActive();

    @Query("select e from JpaPassiveScanMarketEntity e where e.status in ('REGISTERED','DISPATCH_REQUESTED','ACCEPTED','RUNNING') order by e.updatedAt asc")
    List<JpaPassiveScanMarketEntity> findActive();

    @Query("select count(e) from JpaPassiveScanMarketEntity e where e.marketId = :marketId and e.status = 'FAILED'")
    long countFailedAttempts(@Param("marketId") UUID marketId);

    @Modifying
    @Query("update JpaPassiveScanMarketEntity e set e.status = :target, e.updatedAt = :updatedAt, e.version = e.version + 1 "
            + "where e.scanMarketId = :id and e.status = :expected and e.version = :version")
    int transitionStatus(@Param("id") UUID id, @Param("expected") String expected,
                         @Param("target") String target, @Param("version") long version,
                         @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query("update JpaPassiveScanMarketEntity e set e.status = :target, e.leaseId = :leaseId, "
            + "e.leaseExpiresAt = :leaseExpiresAt, e.updatedAt = :updatedAt, e.version = e.version + 1 "
            + "where e.scanMarketId = :id and e.status = :expected and e.version = :version "
            + "and (e.leaseExpiresAt is null or e.leaseExpiresAt < :updatedAt)")
    int claim(@Param("id") UUID id, @Param("expected") String expected,
              @Param("target") String target, @Param("version") long version, @Param("leaseId") UUID leaseId,
              @Param("leaseExpiresAt") Instant leaseExpiresAt, @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query("update JpaPassiveScanMarketEntity e set e.status = :target, e.failureCode = :failureCode, "
            + "e.failureReason = :failureReason, e.attemptCount = :attemptCount, e.nextRetryAt = :nextRetryAt, "
            + "e.leaseId = null, e.leaseExpiresAt = null, e.updatedAt = :updatedAt, e.version = e.version + 1 "
            + "where e.scanMarketId = :id and e.status = :expected and e.leaseId = :leaseId and e.version = :version")
    int recordOutcome(@Param("id") UUID id, @Param("expected") String expected,
                      @Param("target") String target, @Param("failureCode") String failureCode,
                      @Param("failureReason") String failureReason, @Param("attemptCount") int attemptCount,
                      @Param("nextRetryAt") Instant nextRetryAt, @Param("leaseId") UUID leaseId,
                      @Param("version") long version, @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query("update JpaPassiveScanMarketEntity e set e.status = 'REGISTERED', e.leaseId = null, "
            + "e.leaseExpiresAt = null, e.updatedAt = :updatedAt, e.version = e.version + 1 "
            + "where e.scanMarketId = :id and e.status = :expected and e.leaseId = :leaseId "
            + "and e.version = :version and e.leaseExpiresAt < :updatedAt")
    int recoverExpired(@Param("id") UUID id, @Param("expected") String expected,
                       @Param("leaseId") UUID leaseId, @Param("version") long version,
                       @Param("updatedAt") Instant updatedAt);
}
