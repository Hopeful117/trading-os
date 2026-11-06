package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "historical_reconstruction_lifecycles")
class JpaHistoricalReconstructionLifecycleEntity {
    @Id
    @Column(name = "execution_id") UUID executionId;
    @Column(name = "lease_id", nullable = false) UUID leaseId;
    @Column(name = "status", nullable = false, length = 32) String status;
    @Column(name = "phase", nullable = false, length = 64) String phase;
    @Column(name = "failure_code", length = 100) String failureCode;
    @Column(name = "failure_reason", length = 500) String failureReason;
    @Column(name = "snapshot_fingerprint", length = 128) String snapshotFingerprint;
    @Column(name = "outcome_fingerprint", length = 128) String outcomeFingerprint;
    @Column(name = "output_directory", length = 500) String outputDirectory;
    @Column(name = "started_at", nullable = false) Instant startedAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "completed_at") Instant completedAt;
}
