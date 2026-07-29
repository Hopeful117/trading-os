package com.hope.trading.broker_service.connection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "broker_connection")
public class BrokerConnection {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID brokerAccountId;
    @Column(nullable = false, updatable = false)
    private UUID ownerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private BrokerProviderId provider;
    private UUID activeCredentialReference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BrokerConnectionStatus technicalStatus;
    @Column(nullable = false, length = 500)
    private String detectedPermissions;
    private String externalAccountId;
    private String apiKeyHint;
    private Instant lastValidatedAt;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long rowVersion;

    protected BrokerConnection() {
    }

    public static BrokerConnection create(UUID brokerAccountId, UUID ownerId,
                                          BrokerProviderId provider, Instant now) {
        BrokerConnection connection = new BrokerConnection();
        connection.id = UUID.randomUUID();
        connection.brokerAccountId = Objects.requireNonNull(brokerAccountId);
        connection.ownerId = Objects.requireNonNull(ownerId);
        connection.provider = Objects.requireNonNull(provider);
        connection.technicalStatus = BrokerConnectionStatus.CREATED;
        connection.detectedPermissions = "";
        connection.createdAt = now;
        connection.updatedAt = now;
        return connection;
    }

    public void pending(Instant now) {
        technicalStatus = BrokerConnectionStatus.PENDING_VALIDATION;
        updatedAt = now;
    }

    public void validationFailed(BrokerConnectionStatus status, Instant at) {
        if (status != BrokerConnectionStatus.INVALID_CREDENTIALS
                && status != BrokerConnectionStatus.INSUFFICIENT_PERMISSIONS
                && status != BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE) {
            throw new IllegalArgumentException("Invalid validation failure status");
        }
        technicalStatus = status;
        lastValidatedAt = at;
        updatedAt = at;
    }

    public void connected(UUID reference, Set<BrokerPermission> permissions, String externalAccountId,
                          String apiKeyHint, Instant at) {
        activeCredentialReference = Objects.requireNonNull(reference);
        detectedPermissions = permissions.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        this.externalAccountId = externalAccountId;
        this.apiKeyHint = apiKeyHint;
        technicalStatus = BrokerConnectionStatus.CONNECTED;
        lastValidatedAt = at;
        updatedAt = at;
    }

    public void disconnect(Instant at) {
        technicalStatus = BrokerConnectionStatus.DISCONNECTED;
        updatedAt = at;
    }

    public void revoke(Instant at) {
        technicalStatus = BrokerConnectionStatus.REVOKED;
        activeCredentialReference = null;
        updatedAt = at;
    }

    public UUID id() { return id; }
    public UUID brokerAccountId() { return brokerAccountId; }
    public UUID ownerId() { return ownerId; }
    public BrokerProviderId provider() { return provider; }
    public UUID activeCredentialReference() { return activeCredentialReference; }
    public BrokerConnectionStatus technicalStatus() { return technicalStatus; }
    public Set<BrokerPermission> detectedPermissions() {
        if (detectedPermissions == null || detectedPermissions.isBlank()) return Set.of();
        Set<BrokerPermission> result = EnumSet.noneOf(BrokerPermission.class);
        for (String value : detectedPermissions.split(",")) result.add(BrokerPermission.valueOf(value));
        return Collections.unmodifiableSet(result);
    }
    public String externalAccountId() { return externalAccountId; }
    public String apiKeyHint() { return apiKeyHint; }
    public Instant lastValidatedAt() { return lastValidatedAt; }
}
