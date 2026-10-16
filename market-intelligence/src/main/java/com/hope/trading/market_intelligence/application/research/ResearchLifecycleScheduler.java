package com.hope.trading.market_intelligence.application.research;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ResearchLifecycleScheduler {
    private final ResearchLifecycleService lifecycle;

    public ResearchLifecycleScheduler(ResearchLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Scheduled(fixedDelayString = "${research.lifecycle.fixed-delay:PT1M}")
    public void run() {
        lifecycle.runOnce();
    }
}
