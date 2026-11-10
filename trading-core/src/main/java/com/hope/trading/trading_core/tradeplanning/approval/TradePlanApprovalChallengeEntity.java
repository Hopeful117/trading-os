package com.hope.trading.trading_core.tradeplanning.approval;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_plan_approval_challenge")
public class TradePlanApprovalChallengeEntity {
    @Id private UUID id;
    @Column(name = "agent_id", nullable = false, updatable = false) private UUID agentId;
    @Column(name = "owner_user_id", nullable = false, updatable = false) private UUID ownerUserId;
    @Column(name = "trading_account_id", nullable = false, updatable = false) private UUID tradingAccountId;
    @Column(name = "trade_plan_id", nullable = false, updatable = false) private UUID tradePlanId;
    @Column(name = "trade_plan_version", nullable = false, updatable = false) private long tradePlanVersion;
    @Column(name = "risk_evaluation_id", nullable = false, updatable = false) private UUID riskEvaluationId;
    @Column(name = "parameter_digest", nullable = false, length = 64, updatable = false) private String parameterDigest;
    @Column(name = "idempotency_key", nullable = false, length = 200, updatable = false) private String idempotencyKey;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false, updatable = false) private Instant expiresAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TradePlanApprovalStatus status;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decided_by_user_id") private UUID decidedByUserId;
    @Column(name = "reason", length = 256) private String reason;
    @Version private long version;

    protected TradePlanApprovalChallengeEntity() { }

    public static TradePlanApprovalChallengeEntity create(UUID agentId, UUID ownerUserId, UUID accountId,
            UUID planId, long planVersion, UUID evaluationId, String digest, String idempotencyKey, Instant now, Instant expiresAt) {
        var value = new TradePlanApprovalChallengeEntity();
        value.id = UUID.randomUUID(); value.agentId = agentId; value.ownerUserId = ownerUserId;
        value.tradingAccountId = accountId; value.tradePlanId = planId; value.tradePlanVersion = planVersion;
        value.riskEvaluationId = evaluationId; value.parameterDigest = digest; value.createdAt = now;
        value.idempotencyKey = idempotencyKey;
        value.expiresAt = expiresAt; value.status = TradePlanApprovalStatus.PENDING;
        return value;
    }

    public void approve(UUID userId, Instant now) { status = TradePlanApprovalStatus.APPROVED; decidedByUserId = userId; decidedAt = now; }
    public void reject(UUID userId, Instant now) { status = TradePlanApprovalStatus.REJECTED; decidedByUserId = userId; decidedAt = now; }
    public void expire() { if (status == TradePlanApprovalStatus.PENDING) status = TradePlanApprovalStatus.EXPIRED; }
    public UUID id() { return id; } public UUID agentId() { return agentId; } public UUID ownerUserId() { return ownerUserId; }
    public UUID tradingAccountId() { return tradingAccountId; } public UUID tradePlanId() { return tradePlanId; }
    public long tradePlanVersion() { return tradePlanVersion; } public UUID riskEvaluationId() { return riskEvaluationId; }
    public String parameterDigest() { return parameterDigest; } public Instant createdAt() { return createdAt; }
    public String idempotencyKey() { return idempotencyKey; }
    public Instant expiresAt() { return expiresAt; } public TradePlanApprovalStatus status() { return status; }
    public Instant decidedAt() { return decidedAt; } public UUID decidedByUserId() { return decidedByUserId; }
    public String reason() { return reason; }
}
