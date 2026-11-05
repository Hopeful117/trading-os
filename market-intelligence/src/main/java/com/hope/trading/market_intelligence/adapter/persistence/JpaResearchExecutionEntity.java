package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "research_executions")
class JpaResearchExecutionEntity {
    @Id
    @Column(name = "research_id")
    UUID researchId;

    @Column(name = "actor_id", nullable = false)
    UUID actorId;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    String requestFingerprint;

    @Column(name = "market_ids", nullable = false, columnDefinition = "TEXT")
    String marketIds;

    @Column(name = "objective", nullable = false, columnDefinition = "TEXT")
    String objective;

    @Column(name = "depth", nullable = false, length = 32)
    String depth;

    @Column(name = "status", nullable = false, length = 40)
    String status;

    @Column(name = "requested_at", nullable = false)
    Instant requestedAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "failure_code", length = 100)
    String failureCode;

    @Column(name = "report", columnDefinition = "TEXT")
    String report;
}
