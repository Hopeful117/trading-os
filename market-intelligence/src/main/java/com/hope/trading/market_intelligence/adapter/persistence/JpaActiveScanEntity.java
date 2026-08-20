package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "active_scans")
class JpaActiveScanEntity {
    @Id
    @Column(name = "scan_id")
    UUID scanId;

    @Column(name = "actor_id", nullable = false)
    UUID actorId;

    @Column(name = "account_id", nullable = false)
    UUID accountId;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    String requestFingerprint;

    @Column(name = "status", nullable = false, length = 40)
    String status;

    @Column(name = "objective", nullable = false, columnDefinition = "TEXT")
    String objective;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    String payload;

    @Column(name = "resolved_at", nullable = false)
    Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
