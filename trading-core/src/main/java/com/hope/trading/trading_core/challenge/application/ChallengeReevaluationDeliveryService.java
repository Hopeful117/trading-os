package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.infrastructure.persistence.ChallengeReevaluationOutboxPersistence;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;

/** Delivers committed PAPER settlement reevaluation requests with durable retry. */
@Service
@ConditionalOnProperty(prefix = "trading.challenge.reevaluation", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ChallengeReevaluationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(ChallengeReevaluationDeliveryService.class);

    private final ChallengeReevaluationOutboxPersistence outbox;
    private final ChallengeReevaluationService reevaluation;
    private final Clock clock;
    private final ScheduledExecutorService leaseRenewalExecutor = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "challenge-reevaluation-lease-renewal");
        thread.setDaemon(true);
        return thread;
    });

    private static final int MAX_ATTEMPTS = 8;
    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final long RETENTION_DAYS = 30;
    private final long renewalInitialDelayMs;
    private final long renewalPeriodMs;

    @Autowired
    public ChallengeReevaluationDeliveryService(ChallengeReevaluationOutboxPersistence outbox,
                                                 ChallengeReevaluationService reevaluation,
                                                 Clock clock) {
        this(outbox, reevaluation, clock, 60_000, 60_000);
    }

    ChallengeReevaluationDeliveryService(ChallengeReevaluationOutboxPersistence outbox,
                                         ChallengeReevaluationService reevaluation,
                                         Clock clock,
                                         long renewalInitialDelayMs,
                                         long renewalPeriodMs) {
        this.outbox = outbox;
        this.reevaluation = reevaluation;
        this.clock = clock;
        this.renewalInitialDelayMs = renewalInitialDelayMs;
        this.renewalPeriodMs = renewalPeriodMs;
    }

    public void deliver(UUID executionIntentId) {
        outbox.claim(executionIntentId, clock.instant(), true).ifPresent(this::deliverClaim);
    }

    @Scheduled(fixedDelayString = "${trading.challenge.reevaluation.retry-delay-ms:5000}",
            initialDelayString = "${trading.challenge.reevaluation.retry-initial-delay-ms:5000}")
    public void retryDue() {
        Instant now = clock.instant();
        for (UUID executionIntentId : outbox.due(now, 50)) {
            outbox.claim(executionIntentId, now, false).ifPresent(this::deliverClaim);
        }
        outbox.deleteDeliveredBefore(now.minus(RETENTION_DAYS, ChronoUnit.DAYS), CLEANUP_BATCH_SIZE);
    }

    private void deliverClaim(ChallengeReevaluationOutboxPersistence.Delivery delivery) {
        ScheduledFuture<?> renewal = leaseRenewalExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        outbox.renew(delivery.executionIntentId(), delivery.claimToken(), clock.instant());
                    } catch (RuntimeException failure) {
                        log.warn("challenge_reevaluation_lease_renewal_failed executionIntentId={}",
                                delivery.executionIntentId(), failure);
                    }
                },
                renewalInitialDelayMs, renewalPeriodMs, TimeUnit.MILLISECONDS);
        try {
            // Claim commits before reevaluation starts, so no settlement lock is held here.
            reevaluation.reevaluateAccount(delivery.accountId());
            outbox.delivered(delivery.executionIntentId(), delivery.claimToken(), clock.instant());
        } catch (RuntimeException failure) {
            log.error("challenge_reevaluation_delivery_failed executionIntentId={} accountId={}",
                    delivery.executionIntentId(), delivery.accountId(), failure);
            outbox.failed(delivery.executionIntentId(), delivery.claimToken(), clock.instant(), failure.getMessage(),
                    MAX_ATTEMPTS);
        } finally {
            renewal.cancel(false);
        }
    }

    @PreDestroy
    void shutdownLeaseRenewal() {
        leaseRenewalExecutor.shutdownNow();
    }
}
