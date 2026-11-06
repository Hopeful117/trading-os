package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

interface SpringDataAnalysisExecutionRepository
        extends JpaRepository<JpaAnalysisExecutionEntity, UUID> {
    @Query("""
            select entity from JpaAnalysisExecutionEntity entity
             where entity.idempotencyKey = :idempotencyKey
               and entity.expiresAt > :now
               and entity.status not in ('FAILED', 'CANCELLED', 'EXPIRED')
            """)
    Optional<JpaAnalysisExecutionEntity> findReusable(
            @Param("idempotencyKey") String idempotencyKey, @Param("now") Instant now);

    Optional<JpaAnalysisExecutionEntity> findByIdempotencyKey(String idempotencyKey);

    List<JpaAnalysisExecutionEntity> findByMarketIdInAndEvaluationCutoffGreaterThanEqualAndEvaluationCutoffLessThanAndEvidencePolicy(
            Collection<UUID> marketIds, Instant startCutoffInclusive, Instant endCutoffExclusive,
            String evidencePolicy);

    List<JpaAnalysisExecutionEntity> findByExecutionIdIn(Collection<UUID> executionIds);

    Optional<JpaAnalysisExecutionEntity> findFirstByMarketIdOrderByUpdatedAtDesc(UUID marketId);

    @Modifying
    @Query("""
            update JpaAnalysisExecutionEntity entity
               set entity.status = :target,
                   entity.updatedAt = :updatedAt
             where entity.executionId = :executionId
               and entity.status = :expected
            """)
    int transitionStatus(
            @Param("executionId") UUID executionId,
            @Param("expected") String expected,
            @Param("target") String target,
            @Param("updatedAt") Instant updatedAt
    );
}
