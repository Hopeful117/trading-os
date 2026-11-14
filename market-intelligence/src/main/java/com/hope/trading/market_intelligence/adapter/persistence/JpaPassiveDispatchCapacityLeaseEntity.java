package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passive_dispatch_capacity_leases")
class JpaPassiveDispatchCapacityLeaseEntity {
    @Id @Column(name = "lease_id") UUID leaseId;
    @Column(name = "scan_market_id", nullable = false, unique = true) UUID scanMarketId;
    @Column(name = "expires_at", nullable = false) Instant expiresAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;
}
