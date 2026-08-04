package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "observation_evidence")
class JpaObservationEvidenceEntity {
    @Id @Column(name = "evidence_id") UUID evidenceId;
    @Column(name = "observation_id", nullable = false) UUID observationId;
    @Column(name = "capability_execution_id", nullable = false) UUID capabilityExecutionId;
    @Column(name = "artifact_input_fingerprint", nullable = false, length = 64) String artifactInputFingerprint;
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT") String payload;
}
