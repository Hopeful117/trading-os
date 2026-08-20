package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

interface SpringDataAnalysisExecutionRepository
        extends JpaRepository<JpaAnalysisExecutionEntity, UUID> {
    Optional<JpaAnalysisExecutionEntity> findByIdempotencyKey(String idempotencyKey);

    List<JpaAnalysisExecutionEntity> findByExecutionIdIn(Collection<UUID> executionIds);

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
