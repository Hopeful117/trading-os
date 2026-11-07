package com.hope.trading.trading_core.agent.infrastructure;

import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_authorization_audit_event")
public class AgentAuthorizationAuditEventEntity {
    @Id
    private UUID id;

    @Column(name = "agent_id", updatable = false)
    private UUID agentId;

    @Column(name = "owner_user_id", updatable = false)
    private UUID ownerUserId;

    @Column(name = "grant_id", updatable = false)
    private UUID grantId;

    @Convert(converter = com.hope.trading.trading_core.agent.model.AgentPermissionConverter.class)
    @Column(length = 32, updatable = false)
    private AgentPermission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 32, updatable = false)
    private AgentResourceType resourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(nullable = false, length = 16, updatable = false)
    private String decision;

    @Column(nullable = false, length = 64, updatable = false)
    private String operation;

    @Column(name = "reason_code", length = 64, updatable = false)
    private String reasonCode;

    @Column(name = "correlation_id", length = 128, updatable = false)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AgentAuthorizationAuditEventEntity() {
    }

    private AgentAuthorizationAuditEventEntity(UUID agentId, UUID ownerUserId, UUID grantId,
                                               AgentPermission permission,
                                               AgentResourceType resourceType, UUID resourceId,
                                               String operation, String decision, String reasonCode, String correlationId,
                                               Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.agentId = agentId;
        this.ownerUserId = ownerUserId;
        this.grantId = grantId;
        this.permission = permission;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.operation = operation;
        this.decision = decision;
        this.reasonCode = reasonCode;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    public static AgentAuthorizationAuditEventEntity of(UUID agentId, AgentPermission permission,
                                                        AgentResourceType resourceType, UUID resourceId,
                                                        String decision, String reasonCode,
                                                        String correlationId, Instant occurredAt) {
        return new AgentAuthorizationAuditEventEntity(agentId, null, null, permission, resourceType, resourceId,
                "AUTHORIZATION_CHECK", decision, reasonCode, correlationId, occurredAt);
    }

    public static AgentAuthorizationAuditEventEntity grantChange(UUID agentId, UUID ownerUserId, UUID grantId,
                                                                  AgentPermission permission,
                                                                  AgentResourceType resourceType, UUID resourceId,
                                                                  String operation, String correlationId,
                                                                  Instant occurredAt) {
        return new AgentAuthorizationAuditEventEntity(agentId, ownerUserId, grantId, permission, resourceType,
                resourceId, operation, "ALLOWED", null, correlationId, occurredAt);
    }
}
