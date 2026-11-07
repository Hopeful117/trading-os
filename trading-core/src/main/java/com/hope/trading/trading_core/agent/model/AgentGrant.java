package com.hope.trading.trading_core.agent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Convert;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_grant")
public class AgentGrant {
    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Convert(converter = AgentPermissionConverter.class)
    @Column(nullable = false, length = 32, updatable = false)
    private AgentPermission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32, updatable = false)
    private AgentResourceType resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentGrantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 128)
    private String revocationReason;

    @Column(name = "granted_by_user_id", nullable = false, updatable = false)
    private UUID grantedByUserId;

    @Column(name = "active_marker")
    private Integer activeMarker;

    @Version
    @Column(name = "grant_version", nullable = false)
    private long grantVersion;

    protected AgentGrant() {
    }

    private AgentGrant(UUID agentId, AgentPermission permission, AgentResourceType resourceType,
                       UUID resourceId, UUID grantedByUserId, Instant now, Instant expiresAt) {
        if (agentId == null || permission == null || resourceType == null || resourceId == null
                || grantedByUserId == null || now == null) {
            throw new IllegalArgumentException("agent, permission, resource, owner and now are required");
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be after the current time");
        }
        this.id = UUID.randomUUID();
        this.agentId = agentId;
        this.permission = permission;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.status = AgentGrantStatus.ACTIVE;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.grantedByUserId = grantedByUserId;
        this.activeMarker = 1;
    }

    public static AgentGrant create(UUID agentId, AgentPermission permission, AgentResourceType resourceType,
                                    UUID resourceId, UUID grantedByUserId, Instant now, Instant expiresAt) {
        return new AgentGrant(agentId, permission, resourceType, resourceId, grantedByUserId, now, expiresAt);
    }

    public void revoke(Instant now, String reason) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (status == AgentGrantStatus.ACTIVE) {
            status = AgentGrantStatus.REVOKED;
            revokedAt = now;
            revocationReason = normalizeReason(reason);
            activeMarker = null;
        }
    }

    public void expire(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (status == AgentGrantStatus.ACTIVE && expiresAt != null && !expiresAt.isAfter(now)) {
            status = AgentGrantStatus.EXPIRED;
            activeMarker = null;
        }
    }

    public boolean effectiveAt(Instant now) {
        return status == AgentGrantStatus.ACTIVE
                && now != null
                && (expiresAt == null || expiresAt.isAfter(now));
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "OWNER_REQUEST";
        }
        String normalized = reason.strip();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("revocation reason is too long");
        }
        return normalized;
    }

    public UUID id() { return id; }
    public UUID agentId() { return agentId; }
    public AgentPermission permission() { return permission; }
    public AgentResourceType resourceType() { return resourceType; }
    public UUID resourceId() { return resourceId; }
    public AgentGrantStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant revokedAt() { return revokedAt; }
    public String revocationReason() { return revocationReason; }
    public UUID grantedByUserId() { return grantedByUserId; }
    public long grantVersion() { return grantVersion; }
}
