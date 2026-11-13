package com.hope.trading.trading_core.executionauthorization;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_authorization", uniqueConstraints = {
        @UniqueConstraint(name = "uk_execution_authorization_agent_key", columnNames = {"agent_id", "execution_idempotency_key"}),
        @UniqueConstraint(name = "uk_execution_authorization_intent", columnNames = "execution_intent_id")
})
public class ExecutionAuthorizationEntity {
    @Id private UUID id;
    @Column(name = "human_user_id", nullable = false, updatable = false) private UUID humanUserId;
    @Column(name = "agent_id", nullable = false, updatable = false) private UUID agentId;
    @Column(name = "grant_id", updatable = false) private UUID grantId;
    @Column(name = "grant_version", updatable = false) private Long grantVersion;
    @Column(name = "broker_account_id", nullable = false, updatable = false) private UUID brokerAccountId;
    @Column(name = "trading_account_id", nullable = false, updatable = false) private UUID tradingAccountId;
    @Column(name = "provider_identity", nullable = false, length = 120, updatable = false) private String providerIdentity;
    @Column(name = "market_identity", nullable = false, length = 160, updatable = false) private String marketIdentity;
    @Column(name = "canonical_market_id", nullable = false, updatable = false) private UUID canonicalMarketId;
    @Column(name = "trade_plan_id", nullable = false, updatable = false) private UUID tradePlanId;
    @Column(name = "trade_plan_version", nullable = false, updatable = false) private long tradePlanVersion;
    @Column(name = "risk_evaluation_id", nullable = false, updatable = false) private UUID riskEvaluationId;
    @Column(name = "execution_idempotency_key", nullable = false, length = 200, updatable = false) private String executionIdempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false) private String requestFingerprint;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false, updatable = false) private Instant expiresAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ExecutionAuthorizationStatus status;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decided_by_human_user_id") private UUID decidedByHumanUserId;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "consumed_by_agent_id") private UUID consumedByAgentId;
    @Column(name = "execution_intent_id") private UUID executionIntentId;
    @Version private long version;

    protected ExecutionAuthorizationEntity() { }

    public static ExecutionAuthorizationEntity create(UUID humanUserId, UUID agentId, UUID grantId,
            Long grantVersion, UUID brokerAccountId, UUID tradingAccountId, String providerIdentity,
            String marketIdentity, UUID canonicalMarketId, UUID tradePlanId, long tradePlanVersion, UUID riskEvaluationId,
            String idempotencyKey, String fingerprint, Instant now, Instant expiresAt) {
        var value = new ExecutionAuthorizationEntity();
        value.id = UUID.randomUUID();
        value.humanUserId = humanUserId;
        value.agentId = agentId;
        value.grantId = grantId;
        value.grantVersion = grantVersion;
        value.brokerAccountId = brokerAccountId;
        value.tradingAccountId = tradingAccountId;
        value.providerIdentity = providerIdentity;
        value.marketIdentity = marketIdentity;
        value.canonicalMarketId = canonicalMarketId;
        value.tradePlanId = tradePlanId;
        value.tradePlanVersion = tradePlanVersion;
        value.riskEvaluationId = riskEvaluationId;
        value.executionIdempotencyKey = idempotencyKey;
        value.requestFingerprint = fingerprint;
        value.createdAt = now;
        value.expiresAt = expiresAt;
        value.status = ExecutionAuthorizationStatus.PENDING_HUMAN_CONFIRMATION;
        return value;
    }

    public void approve(UUID userId, Instant now) {
        status = ExecutionAuthorizationStatus.APPROVED;
        decidedByHumanUserId = userId;
        decidedAt = now;
    }
    public void reject(UUID userId, Instant now) {
        status = ExecutionAuthorizationStatus.REJECTED;
        decidedByHumanUserId = userId;
        decidedAt = now;
    }
    public void revoke(Instant now) {
        status = ExecutionAuthorizationStatus.REVOKED;
        decidedAt = now;
    }
    public void expire() {
        if (status == ExecutionAuthorizationStatus.PENDING_HUMAN_CONFIRMATION
                || status == ExecutionAuthorizationStatus.APPROVED) {
            status = ExecutionAuthorizationStatus.EXPIRED;
        }
    }
    public void beginConsumption() { status = ExecutionAuthorizationStatus.CONSUMING; }
    public void consume(UUID agentId, UUID intentId, Instant now) {
        status = ExecutionAuthorizationStatus.CONSUMED;
        consumedByAgentId = agentId;
        executionIntentId = intentId;
        consumedAt = now;
    }

    public UUID id() { return id; }
    public UUID humanUserId() { return humanUserId; }
    public UUID agentId() { return agentId; }
    public UUID grantId() { return grantId; }
    public Long grantVersion() { return grantVersion; }
    public UUID brokerAccountId() { return brokerAccountId; }
    public UUID tradingAccountId() { return tradingAccountId; }
    public String providerIdentity() { return providerIdentity; }
    public String marketIdentity() { return marketIdentity; }
    public UUID canonicalMarketId() { return canonicalMarketId; }
    public UUID tradePlanId() { return tradePlanId; }
    public long tradePlanVersion() { return tradePlanVersion; }
    public UUID riskEvaluationId() { return riskEvaluationId; }
    public String executionIdempotencyKey() { return executionIdempotencyKey; }
    public String requestFingerprint() { return requestFingerprint; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public ExecutionAuthorizationStatus status() { return status; }
    public Instant decidedAt() { return decidedAt; }
    public UUID decidedByHumanUserId() { return decidedByHumanUserId; }
    public Instant consumedAt() { return consumedAt; }
    public UUID consumedByAgentId() { return consumedByAgentId; }
    public UUID executionIntentId() { return executionIntentId; }
}
