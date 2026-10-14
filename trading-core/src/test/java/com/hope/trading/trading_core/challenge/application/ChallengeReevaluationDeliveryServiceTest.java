package com.hope.trading.trading_core.challenge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hope.trading.trading_core.challenge.infrastructure.persistence.ChallengeReevaluationOutboxPersistence;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ChallengeReevaluationDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    @Test
    void marksRequestDeliveredAfterSuccessfulReevaluation() {
        UUID executionIntentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        ChallengeReevaluationOutboxPersistence outbox = org.mockito.Mockito.mock(
                ChallengeReevaluationOutboxPersistence.class);
        ChallengeReevaluationService reevaluation = org.mockito.Mockito.mock(ChallengeReevaluationService.class);
        var delivery = new ChallengeReevaluationOutboxPersistence.Delivery(executionIntentId, accountId, claimToken);
        when(outbox.claim(executionIntentId, NOW, true)).thenReturn(Optional.of(delivery));

        new ChallengeReevaluationDeliveryService(outbox, reevaluation, Clock.fixed(NOW, ZoneOffset.UTC))
                .deliver(executionIntentId);

        verify(reevaluation).reevaluateAccount(accountId);
        verify(outbox).delivered(executionIntentId, claimToken, NOW);
    }

    @Test
    void recordsFailureForDurableRetryWhenReevaluationFails() {
        UUID executionIntentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        ChallengeReevaluationOutboxPersistence outbox = org.mockito.Mockito.mock(
                ChallengeReevaluationOutboxPersistence.class);
        ChallengeReevaluationService reevaluation = org.mockito.Mockito.mock(ChallengeReevaluationService.class);
        var delivery = new ChallengeReevaluationOutboxPersistence.Delivery(executionIntentId, accountId, claimToken);
        when(outbox.claim(executionIntentId, NOW, true)).thenReturn(Optional.of(delivery));
        doThrow(new IllegalStateException("risk unavailable"))
                .when(reevaluation).reevaluateAccount(accountId);

        new ChallengeReevaluationDeliveryService(outbox, reevaluation, Clock.fixed(NOW, ZoneOffset.UTC))
                .deliver(executionIntentId);

        verify(outbox).failed(executionIntentId, claimToken, NOW, "risk unavailable", 8);
    }

    @Test
    void renewsAnActiveLeaseDuringLongRunningReevaluation() throws Exception {
        UUID executionIntentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ChallengeReevaluationOutboxPersistence outbox = org.mockito.Mockito.mock(
                ChallengeReevaluationOutboxPersistence.class);
        ChallengeReevaluationService reevaluation = org.mockito.Mockito.mock(ChallengeReevaluationService.class);
        var delivery = new ChallengeReevaluationOutboxPersistence.Delivery(executionIntentId, accountId, claimToken);
        when(outbox.claim(executionIntentId, NOW, true)).thenReturn(Optional.of(delivery));
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(reevaluation).reevaluateAccount(accountId);

        Thread worker = new Thread(() -> new ChallengeReevaluationDeliveryService(
                outbox, reevaluation, Clock.fixed(NOW, ZoneOffset.UTC), 10, 10).deliver(executionIntentId));
        worker.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        verify(outbox, timeout(1_000).atLeastOnce()).renew(executionIntentId, claimToken, NOW);
        release.countDown();
        worker.join(5_000);

        verify(outbox).delivered(executionIntentId, claimToken, NOW);
    }
}
