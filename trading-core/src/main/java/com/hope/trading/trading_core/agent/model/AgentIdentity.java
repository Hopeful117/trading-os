package com.hope.trading.trading_core.agent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_identity")
public class AgentIdentity {
    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 32, updatable = false)
    private AgentType agentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AgentIdentity() {
    }

    private AgentIdentity(UUID id, UUID ownerUserId, String displayName, AgentType agentType, Instant now) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.displayName = displayName;
        this.agentType = agentType;
        this.status = AgentStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static AgentIdentity register(UUID ownerUserId, String displayName, AgentType agentType, Instant now) {
        if (ownerUserId == null || agentType == null || now == null) {
            throw new IllegalArgumentException("ownerUserId, agentType and now are required");
        }
        String normalizedName = normalizeName(displayName);
        return new AgentIdentity(UUID.randomUUID(), ownerUserId, normalizedName, agentType, now);
    }

    public void suspend(Instant now) {
        requireMutable(now);
        if (status == AgentStatus.ACTIVE) {
            status = AgentStatus.SUSPENDED;
            updatedAt = now;
        }
    }

    public void activate(Instant now) {
        requireMutable(now);
        if (status == AgentStatus.SUSPENDED) {
            status = AgentStatus.ACTIVE;
            updatedAt = now;
        }
    }

    public void revoke(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (status != AgentStatus.REVOKED) {
            status = AgentStatus.REVOKED;
            updatedAt = now;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID ownerUserId() {
        return ownerUserId;
    }

    public String displayName() {
        return displayName;
    }

    public AgentType agentType() {
        return agentType;
    }

    public AgentStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    private void requireMutable(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (status == AgentStatus.REVOKED) {
            throw new IllegalStateException("Revoked agent cannot change lifecycle state");
        }
    }

    private static String normalizeName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        String normalized = displayName.strip();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("displayName must not exceed 100 characters");
        }
        return normalized;
    }
}
