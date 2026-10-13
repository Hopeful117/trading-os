package com.hope.trading.trading_core.challenge.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Thin timer adapter for the trigger-independent Challenge monitoring use case. */
@Component
@ConditionalOnProperty(prefix = "trading.challenge.monitoring", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ScheduledChallengeMonitoringTrigger {
    private final ActiveChallengeMonitoringService monitoring;

    public ScheduledChallengeMonitoringTrigger(ActiveChallengeMonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @Scheduled(fixedDelayString = "${trading.challenge.monitoring.interval-ms:5000}")
    public void trigger() {
        monitoring.monitor();
    }
}
