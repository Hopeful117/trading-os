package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_snapshots")
class JpaFeatureSnapshotEntity {
    @Id
    @Column(name = "snapshot_id", nullable = false)
    UUID snapshotId;

    @Column(name = "analysis_execution_id", nullable = false)
    UUID analysisExecutionId;

    @Column(name = "market_id", nullable = false)
    UUID marketId;

    @Column(name = "evaluation_cutoff", nullable = false)
    Instant evaluationCutoff;

    @Column(name = "evidence_policy", nullable = false, length = 40)
    String evidencePolicy;

    @Column(name = "projection_version", nullable = false, length = 80)
    String projectionVersion;

    @Column(name = "context_fingerprint", nullable = false, length = 64)
    String contextFingerprint;

    @Column(name = "snapshot_fingerprint", nullable = false, unique = true, length = 64)
    String snapshotFingerprint;

    @Column(name = "identity_fingerprint", nullable = false, unique = true, length = 64)
    String identityFingerprint;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "feature_payload", nullable = false, columnDefinition = "TEXT")
    String featurePayload;

    @Column(name = "group_payload", nullable = false, columnDefinition = "TEXT")
    String groupPayload;

    @Column(name = "provenance_payload", nullable = false, columnDefinition = "TEXT")
    String provenancePayload;
}
