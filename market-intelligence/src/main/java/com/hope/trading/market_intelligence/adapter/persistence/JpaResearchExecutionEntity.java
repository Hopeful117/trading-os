package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "research_executions")
class JpaResearchExecutionEntity {
    @Id
    @Column(name = "research_id")
    UUID researchId;

    @Column(name = "actor_id", nullable = false)
    UUID actorId;

    @Column(name = "research_scope_id")
    UUID researchScopeId;

    @Column(name = "owner_user_id")
    UUID ownerUserId;

    @Column(name = "initiating_agent_id")
    UUID initiatingAgentId;

    @Column(name = "credential_id")
    UUID credentialId;

    @Column(name = "grant_id")
    UUID grantId;

    @Column(name = "grant_version")
    Long grantVersion;

    @Column(name = "credential_version")
    Integer credentialVersion;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    String requestFingerprint;

    @Column(name = "market_ids", nullable = false, columnDefinition = "TEXT")
    String marketIds;

    @Column(name = "objective", nullable = false, columnDefinition = "TEXT")
    String objective;

    @Column(name = "depth", nullable = false, length = 32)
    String depth;

    @Column(name = "objective_type", nullable = false, length = 64)
    String objectiveType;

    @Column(name = "timeframe", length = 32)
    String timeframe;

    @Column(name = "horizon_seconds")
    Long horizonSeconds;

    @Column(name = "cutoff_start")
    Instant cutoffStart;

    @Column(name = "cutoff_end")
    Instant cutoffEnd;

    @Column(name = "status", nullable = false, length = 40)
    String status;

    @Column(name = "requested_at", nullable = false)
    Instant requestedAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "failure_code", length = 100)
    String failureCode;

    @Column(name = "report", columnDefinition = "TEXT")
    String report;

    @Column(name = "attempt_count", nullable = false)
    int attemptCount;

    @Column(name = "current_stage", nullable = false, length = 64)
    String currentStage;

    @Column(name = "last_progress_at", nullable = false)
    Instant lastProgressAt;
}
