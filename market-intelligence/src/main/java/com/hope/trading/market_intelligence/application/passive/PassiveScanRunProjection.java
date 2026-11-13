package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.domain.passive.PassiveScanMarketStatus;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanRunStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PassiveScanRunProjection(
        UUID runId,
        String idempotencyKey,
        PassiveScanRunStatus status,
        int totalMarkets,
        Map<PassiveScanMarketStatus, Integer> countsByStatus,
        List<MarketSummary> markets,
        List<String> diagnostics
) {
    public record MarketSummary(
            UUID scanMarketId,
            UUID marketId,
            String provider,
            String productType,
            PassiveScanMarketStatus status,
            UUID analysisExecutionId,
            String failureCode
    ) { }
}
