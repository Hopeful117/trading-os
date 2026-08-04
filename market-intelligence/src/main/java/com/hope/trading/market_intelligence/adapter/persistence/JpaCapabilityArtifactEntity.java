package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "capability_artifacts",
        uniqueConstraints = @UniqueConstraint(name = "uq_capability_artifact",
                columnNames = {"analysis_execution_id", "artifact_type", "artifact_version",
                        "parameters_fingerprint", "input_fingerprint"}))
class JpaCapabilityArtifactEntity {
    @Id @Column(name = "artifact_row_id") UUID rowId;
    @Column(name = "analysis_execution_id", nullable = false) UUID analysisExecutionId;
    @Column(name = "artifact_type", nullable = false, length = 120) String artifactType;
    @Column(name = "artifact_version", nullable = false, length = 40) String artifactVersion;
    @Column(name = "parameters_fingerprint", nullable = false, length = 64) String parametersFingerprint;
    @Column(name = "input_fingerprint", nullable = false, length = 64) String inputFingerprint;
    @Column(name = "producing_execution_id") UUID producingExecutionId;
    @Column(name = "produced_at", nullable = false) Instant producedAt;
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT") String payload;
}
