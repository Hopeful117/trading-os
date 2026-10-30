package com.hope.trading.market_data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order-book.replay")
public class OrderBookReplayProperties {
    private int batchSize = 1000;
    private int maximumEvidenceCount = 100_000;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    public int getMaximumEvidenceCount() {
        return maximumEvidenceCount;
    }

    public void setMaximumEvidenceCount(int maximumEvidenceCount) {
        if (maximumEvidenceCount <= 0) {
            throw new IllegalArgumentException("maximumEvidenceCount must be positive");
        }
        this.maximumEvidenceCount = maximumEvidenceCount;
    }
}
