package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.scan.ActiveScan;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanStatus;

import java.time.Instant;
import java.util.UUID;

public record ActiveScanSummary(
        UUID scanId,
        UUID accountId,
        ActiveScanStatus status,
        String objective,
        Instant createdAt,
        Instant updatedAt
) {
    public static ActiveScanSummary from(ActiveScan scan) {
        return new ActiveScanSummary(
                scan.scanId(),
                scan.accountId(),
                scan.status(),
                scan.objective(),
                scan.createdAt(),
                scan.updatedAt()
        );
    }
}
