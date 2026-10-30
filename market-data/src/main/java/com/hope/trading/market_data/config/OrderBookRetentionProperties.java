package com.hope.trading.market_data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "order-book.retention")
public class OrderBookRetentionProperties {
    private boolean destructiveRetirementEnabled;
    private boolean schedulerEnabled;
    private Duration schedulerFixedDelay = Duration.ofHours(1);
    private Duration schedulerInitialDelay = Duration.ofMinutes(1);
    private String storagePath = "/";
    private long minimumFreeBytes = 10L * 1024L * 1024L * 1024L;
    private int retirementBatchSize = 1000;
    private Duration invalidatedDiagnosticRetention = Duration.ofDays(30);
    private String artifactSigningKey = "";

    public boolean isDestructiveRetirementEnabled() {
        return destructiveRetirementEnabled;
    }

    public void setDestructiveRetirementEnabled(boolean destructiveRetirementEnabled) {
        this.destructiveRetirementEnabled = destructiveRetirementEnabled;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    public Duration getSchedulerFixedDelay() {
        return schedulerFixedDelay;
    }

    public void setSchedulerFixedDelay(Duration schedulerFixedDelay) {
        if (schedulerFixedDelay == null || schedulerFixedDelay.isZero() || schedulerFixedDelay.isNegative()) {
            throw new IllegalArgumentException("schedulerFixedDelay must be positive");
        }
        this.schedulerFixedDelay = schedulerFixedDelay;
    }

    public Duration getSchedulerInitialDelay() {
        return schedulerInitialDelay;
    }

    public void setSchedulerInitialDelay(Duration schedulerInitialDelay) {
        if (schedulerInitialDelay == null || schedulerInitialDelay.isNegative()) {
            throw new IllegalArgumentException("schedulerInitialDelay cannot be negative or null");
        }
        this.schedulerInitialDelay = schedulerInitialDelay;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public long getMinimumFreeBytes() {
        return minimumFreeBytes;
    }

    public void setMinimumFreeBytes(long minimumFreeBytes) {
        if (minimumFreeBytes < 0) {
            throw new IllegalArgumentException("minimumFreeBytes cannot be negative");
        }
        this.minimumFreeBytes = minimumFreeBytes;
    }

    public int getRetirementBatchSize() {
        return retirementBatchSize;
    }

    public void setRetirementBatchSize(int retirementBatchSize) {
        if (retirementBatchSize <= 0) {
            throw new IllegalArgumentException("retirementBatchSize must be positive");
        }
        this.retirementBatchSize = retirementBatchSize;
    }

    public Duration getInvalidatedDiagnosticRetention() {
        return invalidatedDiagnosticRetention;
    }

    public void setInvalidatedDiagnosticRetention(Duration invalidatedDiagnosticRetention) {
        if (invalidatedDiagnosticRetention == null || invalidatedDiagnosticRetention.isNegative()) {
            throw new IllegalArgumentException("invalidatedDiagnosticRetention cannot be negative or null");
        }
        this.invalidatedDiagnosticRetention = invalidatedDiagnosticRetention;
    }

    public String getArtifactSigningKey() {
        return artifactSigningKey;
    }

    public void setArtifactSigningKey(String artifactSigningKey) {
        this.artifactSigningKey = artifactSigningKey == null ? "" : artifactSigningKey;
    }

    public String requireArtifactSigningKey() {
        if (artifactSigningKey.isBlank()) {
            throw new IllegalStateException("order-book artifact signing key is required");
        }
        return artifactSigningKey;
    }
}
