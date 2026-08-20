package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanStatus;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveScanResultProjection(
        UUID scanId,
        UUID accountId,
        String objective,
        ActiveScanStatus status,
        List<UUID> requestedMarketIds,
        List<UUID> candidateMarketIds,
        List<UUID> effectiveMarketIds,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt,
        ActiveScanProgress progress,
        List<MarketResult> markets
) {
    public ActiveScanResultProjection {
        requestedMarketIds = List.copyOf(requestedMarketIds);
        candidateMarketIds = List.copyOf(candidateMarketIds);
        effectiveMarketIds = List.copyOf(effectiveMarketIds);
        markets = List.copyOf(markets);
    }

    public record ActiveScanProgress(
            int totalCandidates,
            int eligible,
            int excluded,
            int running,
            int completed,
            int failed,
            int opportunitiesFound
    ) {
    }

    public record MarketResult(
            UUID scanMarketId,
            int ordinal,
            UUID marketId,
            boolean eligible,
            List<MarketEligibilityReason> exclusionReasons,
            UUID analysisExecutionId,
            AnalysisExecutionStatus analysisStatus,
            AnalysisResultQuality resultQuality,
            ActiveScanMarketOutcome outcome,
            Diagnostic diagnostic,
            TradingOpportunity opportunity
    ) {
        public MarketResult {
            exclusionReasons = List.copyOf(exclusionReasons);
        }
    }

    public record Diagnostic(
            String code,
            String message
    ) {
    }
}
