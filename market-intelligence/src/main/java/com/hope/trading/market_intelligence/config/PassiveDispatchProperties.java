package com.hope.trading.market_intelligence.config;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-intelligence.passive-dispatch")
public class PassiveDispatchProperties {
    private boolean enabled;
    private Duration fixedDelay = Duration.ofMinutes(5);
    private int maxMarketsPerRun = 2;
    private int maxConcurrentExecutions = 1;
    private int maxAttempts = 3;
    private Duration claimLease = Duration.ofSeconds(30);
    private Duration retryMaxDelay = Duration.ofMinutes(30);

    @PostConstruct
    void validateAtStartup() {
        validate();
    }

    public void validate() {
        if (fixedDelay.compareTo(Duration.ofMinutes(5)) < 0) throw new IllegalStateException("Passive dispatch delay must be at least five minutes");
        if (maxMarketsPerRun < 1) throw new IllegalStateException("Passive dispatch market limit must be positive");
        if (maxConcurrentExecutions < 1) throw new IllegalStateException("Passive dispatch concurrency must be positive");
        if (maxAttempts < 1) throw new IllegalStateException("Passive dispatch attempts must be positive");
        if (claimLease.isZero() || claimLease.isNegative()) throw new IllegalStateException("Passive dispatch lease must be positive");
        if (retryMaxDelay.isZero() || retryMaxDelay.isNegative()) throw new IllegalStateException("Passive dispatch retry delay must be positive");
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getFixedDelay() { return fixedDelay; }
    public void setFixedDelay(Duration fixedDelay) { this.fixedDelay = fixedDelay; }
    public int getMaxMarketsPerRun() { return maxMarketsPerRun; }
    public void setMaxMarketsPerRun(int maxMarketsPerRun) { this.maxMarketsPerRun = maxMarketsPerRun; }
    public int getMaxConcurrentExecutions() { return maxConcurrentExecutions; }
    public void setMaxConcurrentExecutions(int maxConcurrentExecutions) { this.maxConcurrentExecutions = maxConcurrentExecutions; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getClaimLease() { return claimLease; }
    public void setClaimLease(Duration claimLease) { this.claimLease = claimLease; }
    public Duration getRetryMaxDelay() { return retryMaxDelay; }
    public void setRetryMaxDelay(Duration retryMaxDelay) { this.retryMaxDelay = retryMaxDelay; }
}
