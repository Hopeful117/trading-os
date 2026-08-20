package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataActiveScanMarketRepository extends JpaRepository<JpaActiveScanMarketEntity, UUID> {
    List<JpaActiveScanMarketEntity> findByScanIdOrderByOrdinalAsc(UUID scanId);

    @Modifying
    @Query("""
            update JpaActiveScanMarketEntity entity
               set entity.status = :target,
                   entity.updatedAt = :updatedAt
             where entity.scanMarketId = :scanMarketId
               and entity.status = :expected
            """)
    int transitionStatus(
            @Param("scanMarketId") UUID scanMarketId,
            @Param("expected") String expected,
            @Param("target") String target,
            @Param("updatedAt") Instant updatedAt
    );
}
