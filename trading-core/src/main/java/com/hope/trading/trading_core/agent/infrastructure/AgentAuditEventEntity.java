package com.hope.trading.trading_core.agent.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_audit_event")
public class AgentAuditEventEntity {
    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "credential_id", updatable = false)
    private UUID credentialId;

    @Column(nullable = false, updatable = false, length = 64)
    private String operation;

    @Column(nullable = false, updatable = false, length = 32)
    private String outcome;

    @Column(name = "reason_code", updatable = false, length = 128)
    private String reasonCode;

    @Column(name = "correlation_id", updatable = false, length = 128)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AgentAuditEventEntity() {
    }

    private AgentAuditEventEntity(UUID id, UUID agentId, UUID ownerUserId, UUID credentialId,
                                  String operation, String outcome, String reasonCode,
                                  String correlationId, Instant occurredAt) {
        this.id = id;
        this.agentId = agentId;
        this.ownerUserId = ownerUserId;
        this.credentialId = credentialId;
        this.operation = operation;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    public static AgentAuditEventEntity of(UUID agentId, UUID ownerUserId, UUID credentialId,
                                           String operation, String outcome, String reasonCode,
                                           String correlationId, Instant occurredAt) {
        return new AgentAuditEventEntity(UUID.randomUUID(), agentId, ownerUserId, credentialId,
                operation, outcome, reasonCode, correlationId, occurredAt);
    }

    public String correlationId() {
        return correlationId;
    }
}
