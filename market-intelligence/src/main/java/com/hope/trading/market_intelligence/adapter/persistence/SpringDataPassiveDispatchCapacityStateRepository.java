package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface SpringDataPassiveDispatchCapacityStateRepository
        extends JpaRepository<JpaPassiveDispatchCapacityStateEntity, String> {
    @Modifying
    @Query(value = "update passive_dispatch_capacity_state set active_count = active_count + 1, "
            + "version = version + 1, updated_at = :now "
            + "where capacity_key = 'PASSIVE' and active_count < :capacity", nativeQuery = true)
    int acquire(@Param("capacity") int capacity, @Param("now") Instant now);

    @Modifying
    @Query(value = "update passive_dispatch_capacity_state set active_count = active_count - 1, "
            + "version = version + 1, updated_at = :now "
            + "where capacity_key = 'PASSIVE' and active_count > 0", nativeQuery = true)
    int release(@Param("now") Instant now);
}
