package com.hope.trading.trading_core.risk.application;

import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import java.time.Clock;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RiskAcknowledgmentDeliveryService {
    private final RiskPersistence persistence;
    private final TradePlanRiskPort tradePlans;
    private final Clock clock;

    public RiskAcknowledgmentDeliveryService(RiskPersistence persistence, TradePlanRiskPort tradePlans, Clock clock) {
        this.persistence = persistence;
        this.tradePlans = tradePlans;
        this.clock = clock;
    }

    public void deliver(UUID evaluationId) {
        persistence.claimAcknowledgment(evaluationId, clock.instant(), true).ifPresent(this::deliverClaim);
    }

    // Separate initial delay so a fresh instance does not immediately deliver
    // stale outbox entries at startup (STORY-0020A flaky-root-cause fix); the
    // first delivery pass now waits one full retry period by default.
    @Scheduled(fixedDelayString = "${risk.acknowledgment.retry-delay:5000}",
            initialDelayString = "${risk.acknowledgment.retry-initial-delay:"
                    + "${risk.acknowledgment.retry-delay:5000}}")
    public void retryDue() {
        for (UUID evaluationId : persistence.dueAcknowledgments(clock.instant(), 50)) {
            persistence.claimAcknowledgment(evaluationId, clock.instant(), false).ifPresent(this::deliverClaim);
        }
    }

    private void deliverClaim(RiskPersistence.AcknowledgmentDelivery delivery) {
        try {
            // The claim transaction has completed before this remote operation starts.
            tradePlans.acknowledge(delivery.tradePlanId(), delivery.tradePlanVersion(), delivery.evaluationId(),
                    delivery.decision(), delivery.evaluatedAt());
            persistence.acknowledgmentDelivered(delivery.evaluationId(), delivery.claimToken(), clock.instant());
        } catch (RuntimeException failure) {
            persistence.acknowledgmentFailed(delivery.evaluationId(), delivery.claimToken(), clock.instant(),
                    failure.getMessage());
        }
    }
}
