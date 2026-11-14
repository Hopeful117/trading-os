package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "passive_dispatch_capacity_state")
class JpaPassiveDispatchCapacityStateEntity {
    @Id @Column(name = "capacity_key") String capacityKey;
    @Column(name = "active_count", nullable = false) int activeCount;
    @Column(name = "version", nullable = false) long version;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
}
