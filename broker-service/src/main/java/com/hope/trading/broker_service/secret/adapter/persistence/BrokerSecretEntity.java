package com.hope.trading.broker_service.secret.adapter.persistence;

import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.secret.domain.EncryptedSecret;
import com.hope.trading.broker_service.secret.domain.SecretStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Entity
@Table(name = "broker_secret")
class BrokerSecretEntity {
    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID brokerAccountId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private BrokerProviderId provider;
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String ciphertext;
    @Column(nullable = false, updatable = false)
    private String initializationVector;
    @Column(nullable = false, updatable = false)
    private String algorithm;
    @Column(nullable = false, updatable = false)
    private String keyVersion;
    @Column(nullable = false, updatable = false)
    private long secretVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecretStatus status;
    private String apiKeyHint;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    private Instant activatedAt;
    private Instant revokedAt;
    private UUID replacedById;
    @Version
    private long rowVersion;

    protected BrokerSecretEntity() {
    }

    static BrokerSecretEntity pending(UUID id, UUID accountId, BrokerProviderId provider,
                                      EncryptedSecret encrypted, long version, String hint, Instant now) {
        BrokerSecretEntity entity = new BrokerSecretEntity();
        entity.id = id;
        entity.brokerAccountId = accountId;
        entity.provider = provider;
        entity.ciphertext = Base64.getEncoder().encodeToString(encrypted.ciphertext());
        entity.initializationVector = Base64.getEncoder().encodeToString(encrypted.initializationVector());
        entity.algorithm = encrypted.algorithm();
        entity.keyVersion = encrypted.keyVersion();
        entity.secretVersion = version;
        entity.status = SecretStatus.PENDING;
        entity.apiKeyHint = hint;
        entity.createdAt = now;
        return entity;
    }

    void activate(Instant now) {
        if (status != SecretStatus.PENDING) throw new IllegalStateException("Secret version is not pending");
        status = SecretStatus.ACTIVE;
        activatedAt = now;
    }

    void revoke(UUID replacement, Instant now) {
        if (status != SecretStatus.ACTIVE) throw new IllegalStateException("Secret version is not active");
        status = SecretStatus.REVOKED;
        replacedById = replacement;
        revokedAt = now;
    }

    EncryptedSecret encrypted() {
        return new EncryptedSecret(Base64.getDecoder().decode(ciphertext),
                Base64.getDecoder().decode(initializationVector), algorithm, keyVersion);
    }

    UUID id() { return id; }
    UUID brokerAccountId() { return brokerAccountId; }
    long secretVersion() { return secretVersion; }
    SecretStatus status() { return status; }
}
