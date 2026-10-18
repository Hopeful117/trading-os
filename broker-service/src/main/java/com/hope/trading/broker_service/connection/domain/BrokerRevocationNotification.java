package com.hope.trading.broker_service.connection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "broker_revocation_notification")
public class BrokerRevocationNotification {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID brokerAccountId;
    @Column(nullable = false, updatable = false)
    private UUID ownerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private BrokerConnectionStatus targetStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BrokerRevocationNotificationStatus deliveryStatus;
    @Column(nullable = false)
    private int attemptCount;
    @Column(nullable = false)
    private boolean retryExhausted;
    @Column(nullable = false)
    private Instant nextAttemptAt;
    private Instant lastAttemptAt;
    private Instant leaseUntil;
    @Column(length = 120)
    private String lastErrorCode;
    private String externalAccountId;
    @Column(nullable = false, updatable = false)
    private Instant revokedAt;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long rowVersion;

    protected BrokerRevocationNotification() {
    }

    public static BrokerRevocationNotification pending(UUID brokerAccountId, UUID ownerId,
                                                       String externalAccountId, Instant revokedAt) {
        Objects.requireNonNull(brokerAccountId, "brokerAccountId is required");
        Objects.requireNonNull(ownerId, "ownerId is required");
        Objects.requireNonNull(revokedAt, "revokedAt is required");
        BrokerRevocationNotification notification = new BrokerRevocationNotification();
        notification.id = UUID.randomUUID();
        notification.brokerAccountId = brokerAccountId;
        notification.ownerId = ownerId;
        notification.targetStatus = BrokerConnectionStatus.REVOKED;
        notification.deliveryStatus = BrokerRevocationNotificationStatus.PENDING;
        notification.attemptCount = 0;
        notification.retryExhausted = false;
        notification.nextAttemptAt = revokedAt;
        notification.externalAccountId = externalAccountId;
        notification.revokedAt = revokedAt;
        notification.createdAt = revokedAt;
        notification.updatedAt = revokedAt;
        return notification;
    }

    public boolean dueAt(Instant now) {
        return !retryExhausted
                && deliveryStatus != BrokerRevocationNotificationStatus.DELIVERED
                && !nextAttemptAt.isAfter(now)
                && (leaseUntil == null || !leaseUntil.isAfter(now));
    }

    public void claim(Instant now, Instant leaseUntil) {
        if (!dueAt(now)) throw new IllegalStateException("Revocation notification is not due");
        deliveryStatus = BrokerRevocationNotificationStatus.PENDING;
        attemptCount++;
        lastAttemptAt = now;
        this.leaseUntil = Objects.requireNonNull(leaseUntil);
        updatedAt = now;
    }

    public void delivered(Instant now) {
        deliveryStatus = BrokerRevocationNotificationStatus.DELIVERED;
        this.leaseUntil = null;
        lastErrorCode = null;
        nextAttemptAt = now;
        updatedAt = now;
    }

    public void failed(Instant now, Instant nextAttemptAt, String errorCode, boolean exhausted) {
        deliveryStatus = BrokerRevocationNotificationStatus.FAILED;
        this.leaseUntil = null;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt);
        lastErrorCode = errorCode;
        retryExhausted = exhausted;
        updatedAt = now;
    }

    public UUID id() { return id; }
    public UUID brokerAccountId() { return brokerAccountId; }
    public UUID ownerId() { return ownerId; }
    public BrokerConnectionStatus targetStatus() { return targetStatus; }
    public BrokerRevocationNotificationStatus deliveryStatus() { return deliveryStatus; }
    public int attemptCount() { return attemptCount; }
    public boolean retryExhausted() { return retryExhausted; }
    public Instant nextAttemptAt() { return nextAttemptAt; }
    public Instant revokedAt() { return revokedAt; }
    public String externalAccountId() { return externalAccountId; }
}
