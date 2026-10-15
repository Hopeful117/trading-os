package com.hope.trading.news.infrastructure.alphavantage;

import com.hope.trading.news.application.NewsCatalogService;
import com.hope.trading.news.config.AlphaVantageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "news.provider", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "news.providers.alpha-vantage", name = "enabled", havingValue = "true")
public class AlphaVantageFinancialNewsSynchronizationJob {
    private static final Logger log = LoggerFactory.getLogger(AlphaVantageFinancialNewsSynchronizationJob.class);

    private final NewsCatalogService catalog;
    private final AlphaVantageFinancialNewsSource source;
    private final AlphaVantageProperties properties;

    public AlphaVantageFinancialNewsSynchronizationJob(NewsCatalogService catalog,
                                                       AlphaVantageFinancialNewsSource source,
                                                       AlphaVantageProperties properties) {
        this.catalog = catalog;
        this.source = source;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${news.providers.alpha-vantage.poll-interval:PT6H}")
    public void synchronize() {
        if (!properties.pollingEnabled()) {
            return;
        }
        properties.validate();
        Instant to = Instant.now();
        Instant from = to.minus(properties.lookback());
        try {
            catalog.synchronizeFinancialNews(source, from, to);
            log.info("Alpha Vantage Financial News synchronization completed: rejected={}",
                    source.lastRejectedCount());
        } catch (AlphaVantageFinancialNewsSourceException exception) {
            log.warn("Alpha Vantage Financial News synchronization failed: status={}, reason={}",
                    exception.availability(), exception.getMessage());
        }
    }
}
