package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanStatus;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;
import com.hope.trading.market_intelligence.domain.*;

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
            List<TradingOpportunity> opportunities,
            AnalysisEvidence evidence
    ) {
        public MarketResult(
                UUID scanMarketId, int ordinal, UUID marketId, boolean eligible,
                List<MarketEligibilityReason> exclusionReasons, UUID analysisExecutionId,
                AnalysisExecutionStatus analysisStatus, AnalysisResultQuality resultQuality,
                ActiveScanMarketOutcome outcome, Diagnostic diagnostic,
                List<TradingOpportunity> opportunities) {
            this(scanMarketId, ordinal, marketId, eligible, exclusionReasons, analysisExecutionId,
                    analysisStatus, resultQuality, outcome, diagnostic, opportunities, null);
        }

        public MarketResult {
            exclusionReasons = List.copyOf(exclusionReasons);
            opportunities = List.copyOf(opportunities);
        }
    }

    public record AnalysisEvidence(
            EvidenceAcquisitionPolicy evidencePolicy,
            TradeabilityEvidenceProfile tradeabilityEvidenceProfile,
            Instant evaluationCutoff,
            AnalysisExecutionStatus executionStatus,
            List<ContextEvidence> contextSections,
            List<CapabilityEvidence> capabilityExecutions,
            List<FindingEvidence> findings,
            List<String> warnings
    ) {
        public AnalysisEvidence {
            contextSections = List.copyOf(contextSections);
            capabilityExecutions = List.copyOf(capabilityExecutions);
            findings = List.copyOf(findings);
            warnings = List.copyOf(warnings);
        }

        public static AnalysisEvidence from(AnalysisExecution execution) {
            var result = execution.result().orElse(null);
            var provenance = execution.provenance();
            if (result == null) {
                return new AnalysisEvidence(provenance.evidencePolicy(),
                        provenance.tradeabilityEvidenceProfile(), provenance.evaluationCutoff(),
                        execution.status(), List.of(), List.of(), List.of(), List.of());
            }
            return new AnalysisEvidence(
                    provenance.evidencePolicy(), provenance.tradeabilityEvidenceProfile(),
                    provenance.evaluationCutoff(), execution.status(),
                    result.contextSections().stream().map(ContextEvidence::from).toList(),
                    result.executionMetadata() == null ? List.of() : result.executionMetadata()
                            .capabilityExecutions().stream().map(CapabilityEvidence::from).toList(),
                    result.findings().stream().map(FindingEvidence::from).toList(),
                    result.warnings());
        }
    }

    public record ContextEvidence(
            ContextSectionType type,
            ContextSectionStatus status,
            String source,
            Instant sourceOccurredAt,
            Instant fetchedAt,
            EvidenceAcquisitionPolicy evidencePolicy,
            String evidenceFingerprint,
            Integer observationCount,
            Instant evaluationCutoff,
            String message
    ) {
        static ContextEvidence from(ContextSectionSummary value) {
            return new ContextEvidence(value.type(), value.status(), value.source(),
                    value.sourceOccurredAt(), value.fetchedAt(), value.evidencePolicy(),
                    value.evidenceFingerprint(), value.observationCount(), value.evaluationCutoff(),
                    value.message());
        }
    }

    public record CapabilityEvidence(
            String capabilityId,
            CapabilityExecutionStatus status,
            java.time.Duration duration,
            String message
    ) {
        static CapabilityEvidence from(com.hope.trading.market_intelligence.domain.CapabilityExecution value) {
            return new CapabilityEvidence(value.capabilityId(), value.status(), value.duration(), value.message());
        }
    }

    public record FindingEvidence(
            String capabilityId,
            String title,
            String description,
            Instant generatedAt
    ) {
        static FindingEvidence from(IntelligenceFinding value) {
            return new FindingEvidence(value.capabilityId(), value.title(), value.description(), value.generatedAt());
        }
    }

    public record Diagnostic(
            String code,
            String message
    ) {
    }
}
