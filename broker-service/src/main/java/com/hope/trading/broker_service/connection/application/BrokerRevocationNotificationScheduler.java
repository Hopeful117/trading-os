package com.hope.trading.broker_service.connection.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "broker-revocation.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class BrokerRevocationNotificationScheduler {
    private final BrokerRevocationNotificationDeliveryService delivery;

    public BrokerRevocationNotificationScheduler(BrokerRevocationNotificationDeliveryService delivery) {
        this.delivery = delivery;
    }

    @Scheduled(fixedDelayString = "${broker-revocation.reconciliation.fixed-delay:PT30S}")
    public void deliverDue() {
        delivery.deliverDue();
    }
}
