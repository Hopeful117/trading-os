package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "research_hypotheses")
@IdClass(JpaResearchHypothesisEntity.Identity.class)
class JpaResearchHypothesisEntity {
    @Id
    @Column(name = "hypothesis_id")
    UUID hypothesisId;

    @Id
    @Column(name = "version", nullable = false)
    int version;

    @Column(name = "research_id", nullable = false)
    UUID researchId;

    @Column(name = "market_id", nullable = false)
    UUID marketId;

    @Column(name = "context_fingerprint", nullable = false, length = 128)
    String contextFingerprint;

    @Column(name = "proposal", nullable = false, columnDefinition = "TEXT")
    String proposal;

    @Column(name = "validation_status", nullable = false, length = 64)
    String validationStatus;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    public record Identity(UUID hypothesisId, int version) { }
}
