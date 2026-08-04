package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_executions")
class JpaAnalysisExecutionEntity {
    @Id @Column(name = "execution_id") UUID executionId;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200) String idempotencyKey;
    @Column(name = "status", nullable = false, length = 40) String status;
    @Column(name = "requested_at", nullable = false) Instant requestedAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "expires_at", nullable = false) Instant expiresAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "market_id", nullable = false) UUID marketId;
    @Column(name = "mode", nullable = false, length = 20) String mode;
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT") String payload;
}
