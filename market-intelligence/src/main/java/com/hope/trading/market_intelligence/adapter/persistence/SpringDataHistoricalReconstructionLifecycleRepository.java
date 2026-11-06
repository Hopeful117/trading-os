package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.UUID;

interface SpringDataHistoricalReconstructionLifecycleRepository
        extends JpaRepository<JpaHistoricalReconstructionLifecycleEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from JpaHistoricalReconstructionLifecycleEntity entity where entity.executionId = :executionId")
    java.util.Optional<JpaHistoricalReconstructionLifecycleEntity> findForUpdate(
            @Param("executionId") UUID executionId);
    @Modifying
    @Query("""
            update JpaHistoricalReconstructionLifecycleEntity entity
               set entity.leaseId = :leaseId, entity.updatedAt = :claimedAt
             where entity.executionId = :executionId
               and entity.status = 'RUNNING'
               and entity.updatedAt <= :staleBefore
            """)
    int claimIfStale(@Param("executionId") UUID executionId, @Param("leaseId") UUID leaseId,
                     @Param("staleBefore") java.time.Instant staleBefore,
                     @Param("claimedAt") java.time.Instant claimedAt);
}
