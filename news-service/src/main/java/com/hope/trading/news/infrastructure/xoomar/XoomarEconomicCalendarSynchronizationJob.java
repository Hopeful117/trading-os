package com.hope.trading.news.infrastructure.xoomar;

import com.hope.trading.news.application.EconomicCalendarSourcePort;
import com.hope.trading.news.application.NewsCatalogService;
import com.hope.trading.news.config.XoomarProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "news.xoomar", name = "enabled", havingValue = "true")
public class XoomarEconomicCalendarSynchronizationJob {
    private static final Logger log = LoggerFactory.getLogger(XoomarEconomicCalendarSynchronizationJob.class);

    private final NewsCatalogService catalog;
    private final EconomicCalendarSourcePort source;
    private final XoomarProperties properties;

    public XoomarEconomicCalendarSynchronizationJob(NewsCatalogService catalog,
                                                    EconomicCalendarSourcePort source,
                                                    XoomarProperties properties) {
        this.catalog = catalog;
        this.source = source;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${news.xoomar.poll-interval:PT15M}")
    public void synchronize() {
        properties.validate();
        Instant now = Instant.now();
        try {
            catalog.synchronizeEconomicEvents(source,
                    now.minusSeconds(properties.lookbackDays() * 86400L),
                    now.plusSeconds(properties.lookaheadDays() * 86400L));
        } catch (XoomarEconomicCalendarSourceException exception) {
            log.warn("XOOMAR economic calendar synchronization failed: {}", exception.getMessage());
        }
    }
}
