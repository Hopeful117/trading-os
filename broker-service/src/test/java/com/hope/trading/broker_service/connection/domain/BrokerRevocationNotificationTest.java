package com.hope.trading.broker_service.connection.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerRevocationNotificationTest {
    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    @Test
    void exhaustedNotificationCannotBeClaimedAgain() {
        BrokerRevocationNotification notification = BrokerRevocationNotification.pending(
                UUID.randomUUID(), UUID.randomUUID(), "external-1", NOW);
        notification.claim(NOW, NOW.plusSeconds(60));
        notification.failed(NOW, NOW.plusSeconds(30), "TRADING_CORE_HTTP_503", true);

        assertThat(notification.dueAt(NOW.plusSeconds(30))).isFalse();
        assertThat(notification.retryExhausted()).isTrue();
    }
}
