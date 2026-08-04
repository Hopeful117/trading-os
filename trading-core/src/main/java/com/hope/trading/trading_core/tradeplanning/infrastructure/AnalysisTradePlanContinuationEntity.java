package com.hope.trading.trading_core.tradeplanning.infrastructure;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_trade_plan_continuations",
        uniqueConstraints = @UniqueConstraint(name = "uq_analysis_trade_plan_continuation",
                columnNames = {"analysis_execution_id", "actor_id", "account_id", "idempotency_key"}))
public class AnalysisTradePlanContinuationEntity {
    @Id @Column(name = "continuation_id") private UUID continuationId;
    @Column(name = "analysis_execution_id", nullable = false) private UUID analysisExecutionId;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "idempotency_key", nullable = false, length = 200) private String idempotencyKey;
    @Column(name = "context_id", nullable = false) private UUID contextId;
    @Column(name = "context_version", nullable = false) private long contextVersion;
    @Column(name = "context_captured_at", nullable = false) private Instant contextCapturedAt;
    @Column(name = "profile_id", nullable = false) private UUID profileId;
    @Column(name = "profile_version", nullable = false) private long profileVersion;
    @Column(name = "state", nullable = false, length = 30) private String state;
    @Column(name = "trade_plan_id") private UUID tradePlanId;
    @Column(name = "trade_plan_version") private Long tradePlanVersion;
    @Column(name = "failure_code", length = 80) private String failureCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected AnalysisTradePlanContinuationEntity() { }

    public static AnalysisTradePlanContinuationEntity pending(
            UUID analysisId, UUID actorId, UUID accountId, String key,
            UUID contextId, long contextVersion, Instant capturedAt,
            UUID profileId, long profileVersion, Instant createdAt) {
        AnalysisTradePlanContinuationEntity value = new AnalysisTradePlanContinuationEntity();
        value.continuationId = UUID.randomUUID(); value.analysisExecutionId = analysisId;
        value.actorId = actorId; value.accountId = accountId; value.idempotencyKey = key;
        value.contextId = contextId; value.contextVersion = contextVersion;
        value.contextCapturedAt = capturedAt; value.profileId = profileId;
        value.profileVersion = profileVersion; value.state = "PENDING";
        value.createdAt = createdAt; return value;
    }

    public void complete(UUID planId, long planVersion, Instant at) {
        state = "COMPLETED"; tradePlanId = planId; tradePlanVersion = planVersion;
        completedAt = at;
    }

    public String state() { return state; }
    public UUID contextId() { return contextId; }
    public long contextVersion() { return contextVersion; }
    public UUID profileId() { return profileId; }
    public long profileVersion() { return profileVersion; }
    public Instant contextCapturedAt() { return contextCapturedAt; }
    public UUID tradePlanId() { return tradePlanId; }
    public Long tradePlanVersion() { return tradePlanVersion; }
}
