package com.hope.trading.trading_core.challenge.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading.challenge.monitoring")
public class ChallengeMonitoringProperties {
    private boolean enabled = true;
    private long intervalMs = 5000;
    private int batchSize = 50;

    public boolean enabled() { return enabled; }
    public long intervalMs() { return intervalMs; }
    public int batchSize() { return batchSize; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void setIntervalMs(long intervalMs) {
        if (intervalMs <= 0) throw new IllegalArgumentException("monitoring interval must be positive");
        this.intervalMs = intervalMs;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) throw new IllegalArgumentException("monitoring batch size must be positive");
        this.batchSize = batchSize;
    }
}
