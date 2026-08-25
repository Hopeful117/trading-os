package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataActiveScanRepository extends JpaRepository<JpaActiveScanEntity, UUID> {
    List<JpaActiveScanEntity> findByActorIdOrderByCreatedAtDescScanIdDesc(UUID actorId, Pageable pageable);

    Optional<JpaActiveScanEntity> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey);

    Optional<JpaActiveScanEntity> findByActorIdAndScanId(UUID actorId, UUID scanId);

    @Modifying
    @Query("""
            update JpaActiveScanEntity entity
               set entity.status = :target,
                   entity.updatedAt = :updatedAt
             where entity.scanId = :scanId
               and entity.status = :expected
            """)
    int transitionStatus(
            @Param("scanId") UUID scanId,
            @Param("expected") String expected,
            @Param("target") String target,
            @Param("updatedAt") Instant updatedAt
    );
}
