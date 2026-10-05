package com.hope.trading.news.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "news.xoomar")
public class XoomarProperties {
    private boolean enabled;
    private URI baseUrl = URI.create("https://xoomar.com");
    private Duration pollInterval = Duration.ofMinutes(15);
    private int lookbackDays = 1;
    private int lookaheadDays = 7;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI baseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public int lookbackDays() {
        return lookbackDays;
    }

    public void setLookbackDays(int lookbackDays) {
        this.lookbackDays = lookbackDays;
    }

    public int lookaheadDays() {
        return lookaheadDays;
    }

    public void setLookaheadDays(int lookaheadDays) {
        this.lookaheadDays = lookaheadDays;
    }

    public void validate() {
        if (pollInterval == null || pollInterval.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("XOOMAR poll interval must be at least one minute");
        }
        if (lookbackDays < 0 || lookaheadDays < 0 || lookbackDays + lookaheadDays > 31) {
            throw new IllegalArgumentException("XOOMAR synchronization window must not exceed 31 days");
        }
    }
}
