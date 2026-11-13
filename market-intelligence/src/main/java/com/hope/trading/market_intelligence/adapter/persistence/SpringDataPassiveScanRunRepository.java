package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataPassiveScanRunRepository extends JpaRepository<JpaPassiveScanRunEntity, UUID> {
    Optional<JpaPassiveScanRunEntity> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("update JpaPassiveScanRunEntity e set e.status = :target, e.terminalSummary = :summary, e.updatedAt = :updatedAt, "
            + "e.completedAt = case when :target in ('COMPLETED','PARTIALLY_COMPLETED','FAILED','COMPLETED_NO_WORK') then :updatedAt else e.completedAt end, "
            + "e.dispatchRequestedAt = case when :target = 'DISPATCH_REQUESTED' then :updatedAt else e.dispatchRequestedAt end, "
            + "e.startedAt = case when :target = 'RUNNING' then :updatedAt else e.startedAt end, e.version = e.version + 1 "
            + "where e.runId = :runId and e.status = :expected")
    int transitionStatus(@Param("runId") UUID runId, @Param("expected") String expected,
                         @Param("target") String target, @Param("summary") String summary,
                         @Param("updatedAt") Instant updatedAt);
}
