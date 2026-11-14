package com.hope.trading.market_intelligence.application.passive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "market-intelligence.passive-dispatch", name = "enabled", havingValue = "true")
public class PassiveScanDispatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(PassiveScanDispatchScheduler.class);
    private final PassiveScanDispatchCoordinator coordinator;

    public PassiveScanDispatchScheduler(PassiveScanDispatchCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelayString = "${market-intelligence.passive-dispatch.fixed-delay:PT5M}")
    public void dispatch() {
        PassiveScanDispatchCoordinator.DispatchResult result = coordinator.dispatchOnce();
        if (result.dispatched()) {
            log.info("Passive scan dispatched runId={} marketCount={}", result.runId(), result.marketCount());
        }
    }
}
