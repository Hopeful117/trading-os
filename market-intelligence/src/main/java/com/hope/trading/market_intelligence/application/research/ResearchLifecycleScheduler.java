package com.hope.trading.market_intelligence.application.research;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("!test")
public class ResearchLifecycleScheduler {
    private static final String MARKET_DATA_SERVICE_ID = "market-data";
    private static final Logger log = LoggerFactory.getLogger(ResearchLifecycleScheduler.class);

    private final ResearchLifecycleService lifecycle;
    private final DiscoveryClient discoveryClient;

    public ResearchLifecycleScheduler(ResearchLifecycleService lifecycle, DiscoveryClient discoveryClient) {
        this.lifecycle = lifecycle;
        this.discoveryClient = discoveryClient;
    }

    @Scheduled(fixedDelayString = "${research.lifecycle.fixed-delay:PT1M}")
    public void run() {
        try {
            if (discoveryClient.getInstances(MARKET_DATA_SERVICE_ID).isEmpty()) {
                log.info("Research lifecycle skipped: market-data not discoverable");
                return;
            }
        } catch (RuntimeException exception) {
            log.info("Research lifecycle skipped: market-data discovery check failed errorType={}",
                    exception.getClass().getSimpleName());
            return;
        }
        lifecycle.runOnce();
    }
}
