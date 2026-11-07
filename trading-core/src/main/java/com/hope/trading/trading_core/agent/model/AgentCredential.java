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
@Table(name = "agent_credential")
public class AgentCredential {
    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "credential_digest", nullable = false, unique = true, length = 64, updatable = false)
    private String credentialDigest;

    @Column(name = "credential_fingerprint", nullable = false, length = 16, updatable = false)
    private String credentialFingerprint;

    @Column(name = "credential_version", nullable = false, updatable = false)
    private int credentialVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentCredentialStatus status;

    @Column(name = "active_marker")
    private Integer activeMarker;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AgentCredential() {
    }

    private AgentCredential(UUID id, UUID agentId, String digest, String fingerprint, int credentialVersion,
                            Instant now, Instant expiresAt) {
        this.id = id;
        this.agentId = agentId;
        this.credentialDigest = digest;
        this.credentialFingerprint = fingerprint;
        this.credentialVersion = credentialVersion;
        this.status = AgentCredentialStatus.ACTIVE;
        this.activeMarker = 1;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public static AgentCredential issue(UUID agentId, String digest, String fingerprint, int credentialVersion,
                                        Instant now, Instant expiresAt) {
        if (agentId == null || digest == null || fingerprint == null || now == null || credentialVersion < 1) {
            throw new IllegalArgumentException("credential fields are invalid");
        }
        return new AgentCredential(UUID.randomUUID(), agentId, digest, fingerprint, credentialVersion, now, expiresAt);
    }

    public void revoke(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (status == AgentCredentialStatus.ACTIVE) {
            status = AgentCredentialStatus.REVOKED;
            activeMarker = null;
            revokedAt = now;
        }
    }

    public UUID id() { return id; }
    public UUID agentId() { return agentId; }
    public String credentialFingerprint() { return credentialFingerprint; }
    public int credentialVersion() { return credentialVersion; }
    public AgentCredentialStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant lastUsedAt() { return lastUsedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant revokedAt() { return revokedAt; }

    public boolean isActiveAt(Instant now) {
        return status == AgentCredentialStatus.ACTIVE
                && (expiresAt == null || expiresAt.isAfter(now));
    }
}
