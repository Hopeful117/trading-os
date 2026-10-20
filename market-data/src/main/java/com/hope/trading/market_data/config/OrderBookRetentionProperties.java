package com.hope.trading.market_data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "order-book.retention")
public class OrderBookRetentionProperties {
    private boolean destructiveRetirementEnabled;
    private String storagePath = "/";
    private long minimumFreeBytes = 10L * 1024L * 1024L * 1024L;
    private int retirementBatchSize = 1000;
    private Duration invalidatedDiagnosticRetention = Duration.ofDays(30);

    public boolean isDestructiveRetirementEnabled() {
        return destructiveRetirementEnabled;
    }

    public void setDestructiveRetirementEnabled(boolean destructiveRetirementEnabled) {
        this.destructiveRetirementEnabled = destructiveRetirementEnabled;
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
}
