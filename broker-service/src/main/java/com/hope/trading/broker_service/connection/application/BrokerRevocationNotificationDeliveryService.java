package com.hope.trading.broker_service.connection.application;

import com.hope.trading.broker_service.connection.domain.BrokerRevocationNotification;
import com.hope.trading.broker_service.connection.domain.BrokerRevocationNotificationStatus;
import com.hope.trading.broker_service.connection.integration.TradingCoreBrokerAccountClient;
import com.hope.trading.broker_service.security.ServiceJwtService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BrokerRevocationNotificationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(BrokerRevocationNotificationDeliveryService.class);
    private final BrokerRevocationNotificationRepository repository;
    private final BrokerRevocationNotificationStore store;
    private final TradingCoreBrokerAccountClient tradingCore;
    private final ServiceJwtService serviceJwt;
    private final Clock clock;
    private final BrokerRevocationReconciliationProperties properties;

    public BrokerRevocationNotificationDeliveryService(BrokerRevocationNotificationRepository repository,
                                                         BrokerRevocationNotificationStore store,
                                                         TradingCoreBrokerAccountClient tradingCore,
                                                         ServiceJwtService serviceJwt,
                                                         Clock clock,
                                                         BrokerRevocationReconciliationProperties properties) {
        this.repository = repository;
        this.store = store;
        this.tradingCore = tradingCore;
        this.serviceJwt = serviceJwt;
        this.clock = clock;
        this.properties = properties;
    }

    public void deliverImmediately(UUID notificationId, String authorization) {
        deliver(notificationId, () -> {
            BrokerRevocationNotification notification = store.get(notificationId);
            tradingCore.updateStatus(notification.brokerAccountId(), revokedUpdate(notification), authorization);
        }, true);
    }

    public void deliverScheduled(UUID notificationId) {
        deliver(notificationId, () -> {
            BrokerRevocationNotification notification = store.get(notificationId);
            String authorization = "Bearer " + serviceJwt.issue("trading-core", notification.ownerId());
            tradingCore.updateStatusWithServiceAuthorization(notification.brokerAccountId(),
                    revokedUpdate(notification), authorization);
        }, false);
    }

    public void deliverDue() {
        Instant now = clock.instant();
        List<UUID> ids = repository.findDueIds(
                List.of(BrokerRevocationNotificationStatus.PENDING, BrokerRevocationNotificationStatus.FAILED),
                now, properties.getMaxAttempts(), PageRequest.of(0, properties.getBatchSize()));
        ids.forEach(this::deliverScheduled);
    }

    private void deliver(UUID notificationId, Callback callback, boolean propagateFailure) {
        BrokerRevocationNotification claimed = store.claim(notificationId, properties.getLeaseDuration());
        if (claimed == null) return;
        try {
            callback.send();
            store.markDelivered(notificationId);
        } catch (RuntimeException failure) {
            boolean exhausted = claimed.attemptCount() >= properties.getMaxAttempts();
            store.markFailed(notificationId, clock.instant().plus(properties.getRetryDelay()),
                    safeErrorCode(failure), exhausted);
            log.warn("broker_revocation_callback_failed notification={} account={} attempt={} code={}",
                    claimed.id(), claimed.brokerAccountId(), claimed.attemptCount(), safeErrorCode(failure));
            if (propagateFailure) throw failure;
        }
    }

    private TradingCoreBrokerAccountClient.ConnectionStatusUpdate revokedUpdate(
            BrokerRevocationNotification notification) {
        return new TradingCoreBrokerAccountClient.ConnectionStatusUpdate(
                notification.targetStatus(), null, notification.externalAccountId(), notification.revokedAt());
    }

    private String safeErrorCode(Throwable failure) {
        if (failure instanceof FeignException exception) return "TRADING_CORE_HTTP_" + exception.status();
        return failure.getClass().getSimpleName();
    }

    @FunctionalInterface
    private interface Callback { void send(); }
}
