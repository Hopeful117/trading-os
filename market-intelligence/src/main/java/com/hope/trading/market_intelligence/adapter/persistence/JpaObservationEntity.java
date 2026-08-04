package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "observations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_observation_lineage_version",
                        columnNames = {"lineage_id", "version"}),
                @UniqueConstraint(name = "uq_observation_fingerprint",
                        columnNames = "idempotency_fingerprint")})
class JpaObservationEntity {
    @Id @Column(name = "observation_id") UUID observationId;
    @Column(name = "lineage_id", nullable = false) UUID lineageId;
    @Column(name = "version", nullable = false) long version;
    @Column(name = "instrument", nullable = false, length = 120) String instrument;
    @Column(name = "observation_type", nullable = false, length = 120) String observationType;
    @Column(name = "status", nullable = false, length = 30) String status;
    @Column(name = "consolidation_rule_version", nullable = false, length = 80) String ruleVersion;
    @Column(name = "idempotency_fingerprint", nullable = false, length = 64) String fingerprint;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT") String payload;
}
