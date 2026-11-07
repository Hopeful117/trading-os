package com.hope.trading.trading_core.agent.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_authentication_audit_event")
public class AgentAuthenticationAuditEventEntity {
    @Id
    private UUID id;
    @Column(name = "agent_id", updatable = false)
    private UUID agentId;
    @Column(name = "credential_id", updatable = false)
    private UUID credentialId;
    @Column(name = "owner_user_id", updatable = false)
    private UUID ownerUserId;
    @Column(name = "credential_version", updatable = false)
    private Integer credentialVersion;
    @Column(nullable = false, updatable = false, length = 64)
    private String operation;
    @Column(nullable = false, updatable = false, length = 32)
    private String outcome;
    @Column(name = "reason_code", updatable = false, length = 64)
    private String reasonCode;
    @Column(name = "correlation_id", updatable = false, length = 128)
    private String correlationId;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AgentAuthenticationAuditEventEntity() {
    }

    private AgentAuthenticationAuditEventEntity(UUID id, UUID agentId, UUID credentialId, UUID ownerUserId,
                                                Integer credentialVersion, String operation, String outcome,
                                                String reasonCode, String correlationId, Instant occurredAt) {
        this.id = id;
        this.agentId = agentId;
        this.credentialId = credentialId;
        this.ownerUserId = ownerUserId;
        this.credentialVersion = credentialVersion;
        this.operation = operation;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    public static AgentAuthenticationAuditEventEntity of(UUID agentId, UUID credentialId, UUID ownerUserId,
                                                          Integer credentialVersion, String operation,
                                                          String outcome, String reasonCode, String correlationId,
                                                          Instant occurredAt) {
        return new AgentAuthenticationAuditEventEntity(UUID.randomUUID(), agentId, credentialId, ownerUserId,
                credentialVersion, operation, outcome, reasonCode, correlationId, occurredAt);
    }
}
