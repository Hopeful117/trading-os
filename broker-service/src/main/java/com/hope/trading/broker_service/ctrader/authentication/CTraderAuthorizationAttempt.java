package com.hope.trading.broker_service.ctrader.authentication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ctrader_authorization_attempt")
public class CTraderAuthorizationAttempt {
    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID ownerId;
    @Column(nullable = false, updatable = false)
    private UUID brokerAccountId;
    @Column(nullable = false, updatable = false)
    private UUID providerRegistrationReference;
    @Column(nullable = false, unique = true, updatable = false, length = 128)
    private String stateHash;
    @Column(nullable = false, updatable = false, length = 16)
    private String environment;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant consumedAt;
    private UUID tokenCredentialReference;
    @Column(nullable = false)
    private String discoveredAccountIds = "";
    private String selectedExternalAccountId;
    @Column(nullable = false, length = 64)
    private String permissionScope = "accounts";
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CTraderAuthorizationAttemptStatus status;
    private String failureCode;
    @Version
    private long rowVersion;

    protected CTraderAuthorizationAttempt() {
    }

    public static CTraderAuthorizationAttempt initiate(UUID ownerId, UUID brokerAccountId,
                                                        UUID providerReference, String stateHash,
                                                        String environment, Instant createdAt,
                                                        Instant expiresAt) {
        CTraderAuthorizationAttempt attempt = new CTraderAuthorizationAttempt();
        attempt.id = UUID.randomUUID();
        attempt.ownerId = ownerId;
        attempt.brokerAccountId = brokerAccountId;
        attempt.providerRegistrationReference = providerReference;
        attempt.stateHash = stateHash;
        attempt.environment = environment;
        attempt.createdAt = createdAt;
        attempt.expiresAt = expiresAt;
        attempt.status = CTraderAuthorizationAttemptStatus.INITIATED;
        return attempt;
    }

    public void consume(Instant at) {
        if (status != CTraderAuthorizationAttemptStatus.INITIATED
                || consumedAt != null
                || !at.isBefore(expiresAt)) {
            if (status == CTraderAuthorizationAttemptStatus.INITIATED && !at.isBefore(expiresAt)) {
                status = CTraderAuthorizationAttemptStatus.EXPIRED;
            }
            throw new CTraderAuthenticationException("Authorization attempt is not usable");
        }
        consumedAt = at;
    }

    public void discovered(UUID tokenReference, List<String> accountIds) {
        tokenCredentialReference = tokenReference;
        discoveredAccountIds = String.join(",", accountIds);
        status = CTraderAuthorizationAttemptStatus.ACCOUNT_SELECTION_REQUIRED;
    }

    public void permissionScope(String scope) {
        if (scope == null || scope.isBlank()) throw new IllegalArgumentException("permission scope is required");
        permissionScope = scope;
    }

    public void bind(String externalAccountId) {
        if (!discoveredAccountIds().stream().anyMatch(externalAccountId::equals)) {
            throw new CTraderAuthenticationException("External account was not authorized");
        }
        if (status == CTraderAuthorizationAttemptStatus.BOUND
                && externalAccountId.equals(selectedExternalAccountId)) return;
        if (selectedExternalAccountId != null && !selectedExternalAccountId.equals(externalAccountId)) {
            throw new CTraderAuthenticationException("Changing the bound external account requires a new authorization");
        }
        selectedExternalAccountId = externalAccountId;
        status = CTraderAuthorizationAttemptStatus.BOUND;
    }

    public void fail(String code) {
        status = CTraderAuthorizationAttemptStatus.FAILED;
        failureCode = code;
    }

    public void expire() {
        if (status == CTraderAuthorizationAttemptStatus.INITIATED) status = CTraderAuthorizationAttemptStatus.EXPIRED;
    }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public UUID brokerAccountId() { return brokerAccountId; }
    public UUID providerRegistrationReference() { return providerRegistrationReference; }
    public String stateHash() { return stateHash; }
    public String environment() { return environment; }
    public Instant expiresAt() { return expiresAt; }
    public Instant consumedAt() { return consumedAt; }
    public UUID tokenCredentialReference() { return tokenCredentialReference; }
    public List<String> discoveredAccountIds() {
        if (discoveredAccountIds == null || discoveredAccountIds.isBlank()) return List.of();
        return Arrays.stream(discoveredAccountIds.split(",")).filter(value -> !value.isBlank()).toList();
    }
    public String selectedExternalAccountId() { return selectedExternalAccountId; }
    public String permissionScope() { return permissionScope; }
    public CTraderAuthorizationAttemptStatus status() { return status; }
    public String failureCode() { return failureCode; }
}
