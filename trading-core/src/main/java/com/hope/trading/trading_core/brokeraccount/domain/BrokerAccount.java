package com.hope.trading.trading_core.brokeraccount.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "broker_account")
public class BrokerAccount {
    private static final int MAX_DISPLAY_NAME_LENGTH = 80;
    private static final Map<BrokerConnectionStatus, Set<BrokerConnectionStatus>> TRANSITIONS =
            transitions();

    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID ownerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private BrokerProvider provider;
    @Column(nullable = false, length = MAX_DISPLAY_NAME_LENGTH)
    private String displayName;
    private String externalAccountId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BrokerConnectionStatus connectionStatus;
    @Column(name = "credential_reference")
    private UUID credentialReference;
    private Instant lastValidatedAt;
    private Instant lastSynchronizedAt;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected BrokerAccount() {
    }

    private BrokerAccount(BrokerAccountId id, UUID ownerId, BrokerProvider provider,
                          String displayName, Instant now) {
        this.id = Objects.requireNonNull(id, "id is required").value();
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId is required");
        this.provider = Objects.requireNonNull(provider, "provider is required");
        this.displayName = validateDisplayName(displayName);
        this.connectionStatus = BrokerConnectionStatus.CREATED;
        this.createdAt = Objects.requireNonNull(now, "now is required");
        this.updatedAt = now;
    }

    public static BrokerAccount create(UUID ownerId, BrokerProvider provider,
                                       String displayName, Instant now) {
        return new BrokerAccount(BrokerAccountId.newId(), ownerId, provider, displayName, now);
    }

    public void markPendingValidation(Instant now) {
        transitionTo(BrokerConnectionStatus.PENDING_VALIDATION, now);
    }

    public void markConnected(CredentialReference reference, String externalAccountId, Instant validatedAt) {
        Objects.requireNonNull(reference, "credential reference is required");
        transitionTo(BrokerConnectionStatus.CONNECTED, validatedAt);
        this.credentialReference = reference.value();
        this.externalAccountId = externalAccountId;
        this.lastValidatedAt = validatedAt;
    }

    public void markInvalidCredentials(Instant now) {
        transitionTo(BrokerConnectionStatus.INVALID_CREDENTIALS, now);
    }

    public void markInsufficientPermissions(Instant now) {
        transitionTo(BrokerConnectionStatus.INSUFFICIENT_PERMISSIONS, now);
    }

    public void markTemporarilyUnavailable(Instant now) {
        transitionTo(BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE, now);
    }

    public void markReauthenticationRequired(Instant now) {
        transitionTo(BrokerConnectionStatus.REAUTHENTICATION_REQUIRED, now);
    }

    public void disconnect(Instant now) {
        transitionTo(BrokerConnectionStatus.DISCONNECTED, now);
    }

    public void markRevoked(Instant now) {
        transitionTo(BrokerConnectionStatus.REVOKED, now);
        credentialReference = null;
    }

    private void transitionTo(BrokerConnectionStatus target, Instant now) {
        if (!TRANSITIONS.getOrDefault(connectionStatus, Set.of()).contains(target)) {
            throw new InvalidBrokerConnectionTransitionException(connectionStatus, target);
        }
        connectionStatus = target;
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    private static String validateDisplayName(String value) {
        if (value == null || value.isBlank() || value.strip().length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("displayName must contain between 1 and 80 characters");
        }
        return value.strip();
    }

    private static Map<BrokerConnectionStatus, Set<BrokerConnectionStatus>> transitions() {
        Map<BrokerConnectionStatus, Set<BrokerConnectionStatus>> result =
                new EnumMap<>(BrokerConnectionStatus.class);
        result.put(BrokerConnectionStatus.CREATED, EnumSet.of(BrokerConnectionStatus.PENDING_VALIDATION));
        result.put(BrokerConnectionStatus.PENDING_VALIDATION, EnumSet.of(
                BrokerConnectionStatus.CONNECTED, BrokerConnectionStatus.INVALID_CREDENTIALS,
                BrokerConnectionStatus.INSUFFICIENT_PERMISSIONS, BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE));
        result.put(BrokerConnectionStatus.CONNECTED, EnumSet.of(
                BrokerConnectionStatus.PENDING_VALIDATION, BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE,
                BrokerConnectionStatus.REAUTHENTICATION_REQUIRED, BrokerConnectionStatus.DISCONNECTED,
                BrokerConnectionStatus.REVOKED));
        result.put(BrokerConnectionStatus.INVALID_CREDENTIALS, EnumSet.of(BrokerConnectionStatus.PENDING_VALIDATION));
        result.put(BrokerConnectionStatus.INSUFFICIENT_PERMISSIONS, EnumSet.of(BrokerConnectionStatus.PENDING_VALIDATION));
        result.put(BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE, EnumSet.of(
                BrokerConnectionStatus.CONNECTED, BrokerConnectionStatus.REAUTHENTICATION_REQUIRED,
                BrokerConnectionStatus.PENDING_VALIDATION));
        result.put(BrokerConnectionStatus.REAUTHENTICATION_REQUIRED, EnumSet.of(BrokerConnectionStatus.PENDING_VALIDATION));
        result.put(BrokerConnectionStatus.DISCONNECTED, EnumSet.of(BrokerConnectionStatus.PENDING_VALIDATION,
                BrokerConnectionStatus.REVOKED));
        result.put(BrokerConnectionStatus.REVOKED, EnumSet.noneOf(BrokerConnectionStatus.class));
        return Map.copyOf(result);
    }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public BrokerProvider provider() { return provider; }
    public String displayName() { return displayName; }
    public String externalAccountId() { return externalAccountId; }
    public BrokerConnectionStatus connectionStatus() { return connectionStatus; }
    public CredentialReference credentialReference() {
        return credentialReference == null ? null : new CredentialReference(credentialReference);
    }
    public Instant lastValidatedAt() { return lastValidatedAt; }
    public Instant lastSynchronizedAt() { return lastSynchronizedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
