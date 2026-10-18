package com.hope.trading.broker_service.connection.application;

import com.hope.trading.broker_service.connection.domain.BrokerRevocationNotification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class BrokerRevocationNotificationStore {
    private final BrokerRevocationNotificationRepository repository;
    private final Clock clock;

    public BrokerRevocationNotificationStore(BrokerRevocationNotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public BrokerRevocationNotification claim(UUID notificationId, Duration leaseDuration) {
        Instant now = clock.instant();
        BrokerRevocationNotification notification = repository.findByIdForUpdate(notificationId)
                .orElse(null);
        if (notification == null || !notification.dueAt(now)) return null;
        notification.claim(now, now.plus(leaseDuration));
        return notification;
    }

    @Transactional
    public void markDelivered(UUID notificationId) {
        repository.findByIdForUpdate(notificationId).ifPresent(notification ->
                notification.delivered(clock.instant()));
    }

    @Transactional
    public void markFailed(UUID notificationId, Instant nextAttemptAt, String errorCode, boolean exhausted) {
        Instant now = clock.instant();
        repository.findByIdForUpdate(notificationId).ifPresent(notification ->
                notification.failed(now, nextAttemptAt, errorCode, exhausted));
    }

    @Transactional(readOnly = true)
    public BrokerRevocationNotification get(UUID notificationId) {
        return repository.findById(notificationId)
                .orElseThrow(() -> new IllegalStateException("Revocation notification not found"));
    }
}
