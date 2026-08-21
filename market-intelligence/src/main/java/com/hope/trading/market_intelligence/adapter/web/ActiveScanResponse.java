package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scan.ActiveScanApplicationService;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import com.hope.trading.market_intelligence.application.scan.ActiveScanMarketOutcome;
import com.hope.trading.market_intelligence.application.scan.ActiveScanResultProjection;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanStatus;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveScanResponse(
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
        ProgressResponse progress,
        List<MarketResponse> markets
) {
    static ActiveScanResponse from(
            ActiveScanResultProjection projection, StrategyMatchRepository matches) {
        return new ActiveScanResponse(
                projection.scanId(),
                projection.accountId(),
                projection.objective(),
                projection.status(),
                projection.requestedMarketIds(),
                projection.candidateMarketIds(),
                projection.effectiveMarketIds(),
                projection.resolvedAt(),
                projection.createdAt(),
                projection.updatedAt(),
                ProgressResponse.from(projection.progress()),
                projection.markets().stream()
                        .map(market -> MarketResponse.from(market, matches)).toList()
        );
    }

    /** Minimal truthful Strategy provenance for trader-facing projection. */
    public record StrategyProvenance(UUID strategyMatchId, UUID strategyId, Integer strategyVersion) {
        static StrategyProvenance from(StrategyMatch match) {
            return new StrategyProvenance(match.matchId(), match.strategyId().value(),
                    match.strategyVersion());
        }
    }

    public record ProgressResponse(
            int totalCandidates,
            int eligible,
            int excluded,
            int running,
            int completed,
            int failed,
            int opportunitiesFound
    ) {
        static ProgressResponse from(ActiveScanResultProjection.ActiveScanProgress progress) {
            return new ProgressResponse(
                    progress.totalCandidates(),
                    progress.eligible(),
                    progress.excluded(),
                    progress.running(),
                    progress.completed(),
                    progress.failed(),
                    progress.opportunitiesFound()
            );
        }
    }

    public record MarketResponse(
            UUID scanMarketId,
            int ordinal,
            UUID marketId,
            boolean eligible,
            AnalysisExecutionStatus analysisStatus,
            AnalysisResultQuality resultQuality,
            ActiveScanMarketOutcome outcome,
            UUID analysisExecutionId,
            List<MarketEligibilityReason> exclusionReasons,
            DiagnosticResponse diagnostic,
            OpportunityResponse opportunity,
            StrategyProvenance strategy
    ) {
        static MarketResponse from(
                ActiveScanResultProjection.MarketResult market,
                StrategyMatchRepository matches) {
            return new MarketResponse(
                    market.scanMarketId(),
                    market.ordinal(),
                    market.marketId(),
                    market.eligible(),
                    market.analysisStatus(),
                    market.resultQuality(),
                    market.outcome(),
                    market.analysisExecutionId(),
                    market.exclusionReasons(),
                    DiagnosticResponse.from(market.diagnostic()),
                    market.opportunity() == null ? null : OpportunityResponse.from(market.opportunity()),
                    strategyProvenance(market.opportunity(), matches)
            );
        }

        private static StrategyProvenance strategyProvenance(
                com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity opportunity,
                StrategyMatchRepository matches) {
            if (opportunity == null || opportunity.strategyMatchId().isEmpty()) {
                return null; // historical pre-0012 rows carry no fabricated attribution
            }
            return matches.findById(opportunity.strategyMatchId().get())
                    .map(StrategyProvenance::from).orElse(null);
        }
    }

    public record DiagnosticResponse(
            String code,
            String message
    ) {
        static DiagnosticResponse from(ActiveScanResultProjection.Diagnostic diagnostic) {
            return diagnostic == null ? null : new DiagnosticResponse(
                    diagnostic.code(),
                    diagnostic.message()
            );
        }
    }
}
