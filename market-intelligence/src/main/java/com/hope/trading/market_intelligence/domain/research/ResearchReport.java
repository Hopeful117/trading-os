package com.hope.trading.market_intelligence.domain.research;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record ResearchReport(
        String schemaVersion,
        String objective,
        String depth,
        Instant generatedAt,
        Instant sourceCutoff,
        List<String> hypotheses,
        List<MarketResearchResult> markets,
        List<String> limitations,
        String modelProvenance,
        List<String> diagnostics,
        String conclusion
) {
    public ResearchReport {
        hypotheses = List.copyOf(hypotheses);
        markets = List.copyOf(markets);
        limitations = List.copyOf(limitations);
        diagnostics = List.copyOf(diagnostics);
    }

    public record MarketResearchResult(
            UUID marketId,
            String provider,
            String instrument,
            String productType,
            String status,
            UUID thesisId,
            Long thesisVersion,
            JsonNode deterministicAnalysis,
            String freshness,
            JsonNode provenance,
            List<JsonNode> supportingEvidence,
            List<JsonNode> contradictingEvidence,
            List<JsonNode> missingEvidence,
            List<String> invalidationConditions,
            String aiStatus,
            JsonNode aiInterpretation,
            List<String> diagnostics
    ) {
        public MarketResearchResult {
            supportingEvidence = List.copyOf(supportingEvidence);
            contradictingEvidence = List.copyOf(contradictingEvidence);
            missingEvidence = List.copyOf(missingEvidence);
            invalidationConditions = List.copyOf(invalidationConditions);
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
