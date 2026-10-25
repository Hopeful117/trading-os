package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_theses", uniqueConstraints = {
        @UniqueConstraint(name = "uq_market_thesis_lineage_version",
                columnNames = {"lineage_id", "version"}),
        @UniqueConstraint(name = "uq_market_thesis_fingerprint",
                columnNames = "input_fingerprint")
})
class JpaMarketThesisEntity {
    @Id @Column(name = "thesis_id") UUID thesisId;
    @Column(name = "lineage_id", nullable = false) UUID lineageId;
    @Column(name = "version", nullable = false) long version;
    @Column(name = "market_id", nullable = false) UUID marketId;
    @Column(name = "instrument", nullable = false, length = 120) String instrument;
    @Column(name = "state", nullable = false, length = 30) String state;
    @Column(name = "scenario_direction", nullable = false, length = 20) String scenarioDirection;
    @Column(name = "composer_version", nullable = false, length = 100) String composerVersion;
    @Column(name = "input_fingerprint", nullable = false, length = 64) String inputFingerprint;
    @Column(name = "analysis_execution_id") UUID analysisExecutionId;
    @Column(name = "evaluated_at", nullable = false) Instant evaluatedAt;
    @Column(name = "source_cutoff", nullable = false) Instant sourceCutoff;
    @Column(name = "valid_from", nullable = false) Instant validFrom;
    @Column(name = "valid_until") Instant validUntil;
    @Column(name = "supersedes") UUID supersedes;
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT") String payload;

    @PreUpdate
    void rejectMutation() {
        throw new IllegalStateException("Market Thesis versions are immutable");
    }
}
