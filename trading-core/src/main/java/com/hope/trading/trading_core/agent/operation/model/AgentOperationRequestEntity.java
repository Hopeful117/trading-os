package com.hope.trading.trading_core.agent.operation.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_operation_request")
public class AgentOperationRequestEntity {
    @Id private UUID id;
    @Column(name = "agent_id", nullable = false, updatable = false) private UUID agentId;
    @Column(name = "owner_user_id", nullable = false, updatable = false) private UUID ownerUserId;
    @Enumerated(EnumType.STRING) @Column(name = "operation_type", nullable = false, updatable = false) private AgentOperationType operationType;
    @Column(name = "resource_type", nullable = false, updatable = false) private String resourceType;
    @Column(name = "resource_id", nullable = false, updatable = false) private UUID resourceId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false, updatable = false) private Instant expiresAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AgentOperationStatus status;
    @Column(name = "correlation_id", length = 128, updatable = false) private String correlationId;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decided_by_user_id") private UUID decidedByUserId;
    @Column(name = "executed_at") private Instant executedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "grant_id", nullable = false, updatable = false) private UUID grantId;
    @Column(name = "grant_version", nullable = false, updatable = false) private long grantVersion;
    @Column(name = "agent_version", nullable = false, updatable = false) private long agentVersion;
    @Column(name = "outcome", length = 64) private String outcome;
    @Column(name = "failure_classification", length = 32) private String failureClassification;
    @Version private long version;

    protected AgentOperationRequestEntity() {}

    public static AgentOperationRequestEntity create(UUID agentId, UUID ownerUserId, AgentOperationType type,
                                                     UUID resourceId, Instant now, Instant expiresAt, String correlationId,
                                                     UUID grantId, long grantVersion, long agentVersion) {
        var value = new AgentOperationRequestEntity();
        value.id = UUID.randomUUID(); value.agentId = agentId; value.ownerUserId = ownerUserId;
        value.operationType = type; value.resourceType = "BROKER_ACCOUNT"; value.resourceId = resourceId;
        value.createdAt = now; value.expiresAt = expiresAt; value.status = AgentOperationStatus.PENDING_APPROVAL;
        value.correlationId = correlationId; value.grantId = grantId;
        value.grantVersion = grantVersion; value.agentVersion = agentVersion;
        return value;
    }
    public static AgentOperationRequestEntity create(UUID agentId, UUID ownerUserId, AgentOperationType type,
                                                     UUID resourceId, Instant now, Instant expiresAt, String correlationId) {
        return create(agentId, ownerUserId, type, resourceId, now, expiresAt, correlationId, UUID.randomUUID(), 0, 0);
    }
    public void approve(UUID userId, Instant now) { status = AgentOperationStatus.APPROVED; decidedByUserId = userId; decidedAt = now; }
    public void reject(UUID userId, Instant now) { status = AgentOperationStatus.REJECTED; decidedByUserId = userId; decidedAt = now; }
    public void expire() { if (status == AgentOperationStatus.PENDING_APPROVAL || status == AgentOperationStatus.APPROVED) status = AgentOperationStatus.EXPIRED; }
    public void succeeded(Instant now) { status = AgentOperationStatus.SUCCEEDED; completedAt = now; outcome = "SUCCEEDED"; }
    public void unknown(Instant now, String reason) { status = AgentOperationStatus.OUTCOME_UNKNOWN; completedAt = now; outcome = "OUTCOME_UNKNOWN"; failureClassification = reason; }
    public void unknown(Instant now) { unknown(now, "TRANSPORT_OR_BROKER_UNCERTAIN"); }
    public void failed(Instant now, String reason) { status = AgentOperationStatus.FAILED; completedAt = now; outcome = "FAILED"; failureClassification = reason; }
    public UUID id() { return id; } public UUID agentId() { return agentId; } public UUID ownerUserId() { return ownerUserId; }
    public AgentOperationType operationType() { return operationType; } public String resourceType() { return resourceType; }
    public UUID resourceId() { return resourceId; } public Instant createdAt() { return createdAt; } public Instant expiresAt() { return expiresAt; }
    public AgentOperationStatus status() { return status; } public String correlationId() { return correlationId; }
    public Instant decidedAt() { return decidedAt; } public UUID decidedByUserId() { return decidedByUserId; }
    public Instant executedAt() { return executedAt; } public String outcome() { return outcome; }
    public Instant completedAt() { return completedAt; } public UUID grantId() { return grantId; }
    public long grantVersion() { return grantVersion; } public long agentVersion() { return agentVersion; }
    public String failureClassification() { return failureClassification; }
}
