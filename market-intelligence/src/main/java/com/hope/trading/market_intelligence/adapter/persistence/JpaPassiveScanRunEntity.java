package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passive_scan_runs")
class JpaPassiveScanRunEntity {
    @Id @Column(name = "run_id") UUID runId;
    @Column(name = "idempotency_key", nullable = false, length = 200) String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) String requestFingerprint;
    @Column(name = "trigger_context", nullable = false, length = 80) String triggerContext;
    @Column(name = "planned_window_start", nullable = false) Instant plannedWindowStart;
    @Column(name = "planned_window_end", nullable = false) Instant plannedWindowEnd;
    @Column(name = "configuration_version", nullable = false, length = 80) String configurationVersion;
    @Column(name = "fingerprint_algorithm", nullable = false, length = 80) String fingerprintAlgorithm;
    @Column(name = "universe_fingerprint", nullable = false, length = 64) String universeFingerprint;
    @Column(name = "resolved_at", nullable = false) Instant resolvedAt;
    @Column(name = "status", nullable = false, length = 40) String status;
    @Column(name = "diagnostics", nullable = false, columnDefinition = "TEXT") String diagnostics;
    @Column(name = "terminal_summary", nullable = false, length = 500) String terminalSummary;
    @Column(name = "dispatch_requested_at") Instant dispatchRequestedAt;
    @Column(name = "started_at") Instant startedAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "version", nullable = false) long version;
}
