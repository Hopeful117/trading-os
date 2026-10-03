package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.marketdata.OhlcInterval;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "market-intelligence.eligibility")
public class MarketEligibilityProperties {
    private OhlcInterval interval;
    private long activityWindowMinutes;
    private int readinessLookbackCandles;
    private int minimumCompletedCandles;
    private long maxObservationAgeSeconds;
    private int maxMarketFactEvaluationsPerScan;

    @PostConstruct
    void validate() {
        if (interval == null || activityWindowMinutes < 1 || readinessLookbackCandles < 1
                || minimumCompletedCandles < 1 || minimumCompletedCandles > readinessLookbackCandles
                || maxObservationAgeSeconds < 1 || maxMarketFactEvaluationsPerScan < 1) {
            throw new IllegalStateException("Valid market eligibility configuration is required");
        }
    }

    public OhlcInterval interval() { return interval; }
    public void setInterval(OhlcInterval interval) { this.interval = interval; }
    public long activityWindowMinutes() { return activityWindowMinutes; }
    public void setActivityWindowMinutes(long value) { this.activityWindowMinutes = value; }
    public int readinessLookbackCandles() { return readinessLookbackCandles; }
    public void setReadinessLookbackCandles(int value) { this.readinessLookbackCandles = value; }
    public int minimumCompletedCandles() { return minimumCompletedCandles; }
    public void setMinimumCompletedCandles(int value) { this.minimumCompletedCandles = value; }
    public long maxObservationAgeSeconds() { return maxObservationAgeSeconds; }
    public void setMaxObservationAgeSeconds(long value) { this.maxObservationAgeSeconds = value; }
    public int maxMarketFactEvaluationsPerScan() { return maxMarketFactEvaluationsPerScan; }
    public void setMaxMarketFactEvaluationsPerScan(int value) { this.maxMarketFactEvaluationsPerScan = value; }
}
