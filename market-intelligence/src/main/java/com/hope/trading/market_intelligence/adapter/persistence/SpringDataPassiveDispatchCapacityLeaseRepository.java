package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface SpringDataPassiveDispatchCapacityLeaseRepository
        extends JpaRepository<JpaPassiveDispatchCapacityLeaseEntity, UUID> {
    @Modifying
    @Query("delete from JpaPassiveDispatchCapacityLeaseEntity e where e.leaseId = :leaseId")
    int deleteLease(@Param("leaseId") UUID leaseId);

    @Modifying
    @Query("delete from JpaPassiveDispatchCapacityLeaseEntity e where e.leaseId = :leaseId and e.expiresAt < :now")
    int deleteExpiredLease(@Param("leaseId") UUID leaseId, @Param("now") Instant now);
}
