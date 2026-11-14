package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passive_scan_markets")
class JpaPassiveScanMarketEntity {
    @Id @Column(name = "scan_market_id") UUID scanMarketId;
    @Column(name = "run_id", nullable = false) UUID runId;
    @Column(name = "ordinal", nullable = false) int ordinal;
    @Column(name = "market_id", nullable = false) UUID marketId;
    @Column(name = "provider", nullable = false, length = 80) String provider;
    @Column(name = "product_type", nullable = false, length = 80) String productType;
    @Column(name = "cadence_seconds", nullable = false) long cadenceSeconds;
    @Column(name = "minimum_evidence_profile", nullable = false, length = 40) String minimumEvidenceProfile;
    @Column(name = "status", nullable = false, length = 40) String status;
    @Column(name = "analysis_execution_id") UUID analysisExecutionId;
    @Column(name = "attempt_count", nullable = false) int attemptCount;
    @Column(name = "failure_code", length = 80) String failureCode;
    @Column(name = "failure_reason", length = 500) String failureReason;
    @Column(name = "next_retry_at") Instant nextRetryAt;
    @Column(name = "lease_id") UUID leaseId;
    @Column(name = "lease_expires_at") Instant leaseExpiresAt;
    @Column(name = "dispatch_requested_at") Instant dispatchRequestedAt;
    @Column(name = "accepted_at") Instant acceptedAt;
    @Column(name = "started_at") Instant startedAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "version", nullable = false) long version;

    Duration cadence() { return Duration.ofSeconds(cadenceSeconds); }
}
