package com.hope.trading.trading_core.agent.operation.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "agent_operation_audit_event")
public class AgentOperationAuditEventEntity {
    @Id private UUID id;
    @Column(name="request_id", nullable=false, updatable=false) private UUID requestId;
    @Column(name="agent_id", nullable=false, updatable=false) private UUID agentId;
    @Column(name="owner_user_id", nullable=false, updatable=false) private UUID ownerUserId;
    @Column(name="operation_type", nullable=false, updatable=false) private String operationType;
    @Column(name="resource_id", nullable=false, updatable=false) private UUID resourceId;
    @Column(nullable=false, updatable=false) private String event;
    @Column(length=64, updatable=false) private String outcome;
    @Column(name="actor_type", length=32, updatable=false) private String actorType;
    @Column(name="actor_id", updatable=false) private UUID actorId;
    @Column(name="broker_invocation_id", updatable=false) private UUID brokerInvocationId;
    @Column(name="failure_classification", length=32, updatable=false) private String failureClassification;
    @Column(name="correlation_id", length=128, updatable=false) private String correlationId;
    @Column(name="occurred_at", nullable=false, updatable=false) private Instant occurredAt;
    protected AgentOperationAuditEventEntity() {}
    public static AgentOperationAuditEventEntity of(UUID requestId, UUID agentId, UUID ownerId, String operation,
                                                     UUID resourceId, String event, String outcome, String correlationId,
                                                     String actorType, UUID actorId, UUID brokerInvocationId,
                                                     String failureClassification, Instant now) {
        var value = new AgentOperationAuditEventEntity(); value.id=UUID.randomUUID(); value.requestId=requestId;
        value.agentId=agentId; value.ownerUserId=ownerId; value.operationType=operation; value.resourceId=resourceId;
        value.event=event; value.outcome=outcome; value.correlationId=correlationId;
        value.actorType=actorType; value.actorId=actorId; value.brokerInvocationId=brokerInvocationId;
        value.failureClassification=failureClassification; value.occurredAt=now; return value;
    }
}
