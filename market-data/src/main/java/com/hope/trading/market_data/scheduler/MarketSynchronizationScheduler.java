package com.hope.trading.market_data.scheduler;

import com.hope.trading.market_data.service.MarketSynchronization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class MarketSynchronizationScheduler {

    private final MarketSynchronization marketSynchronization;


    @Scheduled(
            fixedDelayString = "${market-synchronization.fixed-delay:PT1H}",
            initialDelayString = "${market-data.startup-grace:PT1M}")
    public void synchronizeMarkets() {

        log.info("Starting market synchronization");

        marketSynchronization.synchronizeMarkets();

        log.info("Market synchronization completed");

    }

}
