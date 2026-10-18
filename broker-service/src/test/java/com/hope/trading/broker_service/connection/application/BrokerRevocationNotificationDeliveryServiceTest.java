package com.hope.trading.broker_service.connection.application;

import com.hope.trading.broker_service.connection.domain.BrokerRevocationNotification;
import com.hope.trading.broker_service.connection.integration.TradingCoreBrokerAccountClient;
import com.hope.trading.broker_service.security.ServiceJwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerRevocationNotificationDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    @Mock BrokerRevocationNotificationRepository repository;
    @Mock BrokerRevocationNotificationStore store;
    @Mock TradingCoreBrokerAccountClient tradingCore;
    @Mock ServiceJwtService serviceJwt;

    @Test
    void immediateDeliveryMarksNotificationDelivered() {
        BrokerRevocationNotification notification = notification();
        when(store.claim(notification.id(), java.time.Duration.ofSeconds(60))).thenReturn(notification);
        when(store.get(notification.id())).thenReturn(notification);

        service().deliverImmediately(notification.id(), "Bearer user");

        verify(tradingCore).updateStatus(eq(notification.brokerAccountId()), any(), eq("Bearer user"));
        verify(store).markDelivered(notification.id());
    }

    @Test
    void immediateDeliveryPersistsFailureBeforePropagatingError() {
        BrokerRevocationNotification notification = notification();
        when(store.claim(notification.id(), java.time.Duration.ofSeconds(60))).thenReturn(notification);
        when(store.get(notification.id())).thenReturn(notification);
        doThrow(new IllegalStateException("callback failed"))
                .when(tradingCore).updateStatus(eq(notification.brokerAccountId()), any(), eq("Bearer user"));

        assertThatThrownBy(() -> service().deliverImmediately(notification.id(), "Bearer user"))
                .isInstanceOf(IllegalStateException.class);

        verify(store).markFailed(eq(notification.id()), any(), eq("IllegalStateException"), eq(false));
    }

    @Test
    void scheduledDeliveryUsesOwnerScopedServiceAuthorization() {
        BrokerRevocationNotification notification = notification();
        when(store.claim(notification.id(), java.time.Duration.ofSeconds(60))).thenReturn(notification);
        when(store.get(notification.id())).thenReturn(notification);
        when(serviceJwt.issue("trading-core", notification.ownerId())).thenReturn("service-token");

        service().deliverScheduled(notification.id());

        verify(tradingCore).updateStatusWithServiceAuthorization(eq(notification.brokerAccountId()), any(),
                eq("Bearer service-token"));
        verify(store).markDelivered(notification.id());
    }

    @Test
    void failedDeliveryAtMaximumAttemptsIsMarkedExhausted() {
        BrokerRevocationNotification notification = notification();
        notification.claim(NOW, NOW.plusSeconds(60));
        BrokerRevocationReconciliationProperties properties = properties();
        properties.setMaxAttempts(1);
        when(store.claim(notification.id(), properties.getLeaseDuration())).thenReturn(notification);
        when(store.get(notification.id())).thenReturn(notification);
        doThrow(new IllegalStateException("callback failed"))
                .when(tradingCore).updateStatus(eq(notification.brokerAccountId()), any(), eq("Bearer user"));

        assertThatThrownBy(() -> service(properties).deliverImmediately(notification.id(), "Bearer user"))
                .isInstanceOf(IllegalStateException.class);

        verify(store).markFailed(eq(notification.id()), any(), eq("IllegalStateException"), eq(true));
    }

    private BrokerRevocationNotificationDeliveryService service() {
        return service(properties());
    }

    private BrokerRevocationNotificationDeliveryService service(BrokerRevocationReconciliationProperties properties) {
        return new BrokerRevocationNotificationDeliveryService(repository, store, tradingCore, serviceJwt,
                Clock.fixed(NOW, ZoneOffset.UTC), properties);
    }

    private BrokerRevocationReconciliationProperties properties() {
        return new BrokerRevocationReconciliationProperties();
    }

    private BrokerRevocationNotification notification() {
        return BrokerRevocationNotification.pending(UUID.randomUUID(), UUID.randomUUID(), "external-1", NOW);
    }
}
