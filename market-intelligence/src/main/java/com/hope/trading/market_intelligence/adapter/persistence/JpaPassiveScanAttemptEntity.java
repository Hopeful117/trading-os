package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passive_scan_market_attempts")
class JpaPassiveScanAttemptEntity {
    @Id @Column(name = "attempt_id") UUID attemptId;
    @Column(name = "scan_market_id", nullable = false) UUID scanMarketId;
    @Column(name = "attempt_number", nullable = false) int attemptNumber;
    @Column(name = "analysis_execution_id", nullable = false) UUID analysisExecutionId;
    @Column(name = "idempotency_key", nullable = false, length = 200) String idempotencyKey;
    @Column(name = "created_at", nullable = false) Instant createdAt;
}
