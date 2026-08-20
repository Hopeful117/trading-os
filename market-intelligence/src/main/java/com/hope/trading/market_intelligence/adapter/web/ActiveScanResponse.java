package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scan.ActiveScanApplicationService;
import com.hope.trading.market_intelligence.domain.scan.*;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveScanResponse(
        UUID scanId,
        UUID actorId,
        UUID accountId,
        String objective,
        ActiveScanStatus status,
        String idempotencyKey,
        List<UUID> requestedMarketIds,
        List<UUID> candidateMarketIds,
        List<UUID> effectiveMarketIds,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt,
        List<MarketResponse> markets
) {
    static ActiveScanResponse from(ActiveScanApplicationService.ActiveScanView view) {
        ActiveScan scan = view.scan();
        ActiveScanScopeSnapshot snapshot = scan.scopeSnapshot();
        return new ActiveScanResponse(
                scan.scanId(),
                scan.actorId(),
                scan.accountId(),
                scan.objective(),
                scan.status(),
                scan.idempotencyKey(),
                snapshot.requestedMarketIds(),
                snapshot.candidateMarketIds(),
                snapshot.effectiveMarketIds(),
                snapshot.resolvedAt(),
                scan.createdAt(),
                scan.updatedAt(),
                view.markets().stream().map(MarketResponse::from).toList()
        );
    }

    public record MarketResponse(
            UUID scanMarketId,
            int ordinal,
            UUID marketId,
            boolean eligible,
            ActiveScanMarketStatus status,
            UUID analysisExecutionId,
            List<MarketEligibilityReason> exclusionReasons
    ) {
        static MarketResponse from(ActiveScanMarket market) {
            return new MarketResponse(
                    market.scanMarketId(),
                    market.ordinal(),
                    market.marketId(),
                    market.eligible(),
                    market.status(),
                    market.analysisExecutionId(),
                    market.exclusionReasons()
            );
        }
    }
}
