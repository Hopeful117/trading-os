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
        List<AdaptiveCapabilityRecord> requestedCapabilities,
        List<AdaptiveCapabilityRecord> executedCapabilities,
        List<AdaptiveCapabilityRecord> rejectedCapabilities,
        List<String> limitations,
        String modelProvenance,
        List<String> diagnostics,
        String conclusion,
        ResearchObjectiveType objectiveType,
        ResearchOutcome outcome,
        List<HypothesisProposal> hypothesisProposals
) {
    public ResearchReport(
            String schemaVersion, String objective, String depth, Instant generatedAt,
            Instant sourceCutoff, List<String> hypotheses, List<MarketResearchResult> markets,
            List<AdaptiveCapabilityRecord> requestedCapabilities,
            List<AdaptiveCapabilityRecord> executedCapabilities,
            List<AdaptiveCapabilityRecord> rejectedCapabilities, List<String> limitations,
            String modelProvenance, List<String> diagnostics, String conclusion) {
        this(schemaVersion, objective, depth, generatedAt, sourceCutoff, hypotheses, markets,
                requestedCapabilities, executedCapabilities, rejectedCapabilities, limitations,
                modelProvenance, diagnostics, conclusion,
                ResearchObjectiveType.COMPARATIVE_MARKET_RESEARCH, null, List.of());
    }

    public ResearchReport {
        hypotheses = List.copyOf(hypotheses);
        markets = List.copyOf(markets);
        requestedCapabilities = List.copyOf(requestedCapabilities);
        executedCapabilities = List.copyOf(executedCapabilities);
        rejectedCapabilities = List.copyOf(rejectedCapabilities);
        limitations = List.copyOf(limitations);
        diagnostics = List.copyOf(diagnostics);
        hypothesisProposals = hypothesisProposals == null ? List.of() : List.copyOf(hypothesisProposals);
    }

    public record HypothesisProposal(
            UUID hypothesisId,
            int version,
            UUID marketId,
            String observedBehavior,
            String proposedRelationship,
            String expectedOutcome,
            String evaluationHorizon,
            List<String> applicabilityConditions,
            List<String> falsificationConditions,
            List<UUID> supportingEvidenceReferences,
            List<String> contradictions,
            List<String> missingEvidence,
            List<String> limitations,
            String validationStatus,
            String generator
    ) {
        public HypothesisProposal {
            applicabilityConditions = applicabilityConditions == null ? List.of() : List.copyOf(applicabilityConditions);
            falsificationConditions = falsificationConditions == null ? List.of() : List.copyOf(falsificationConditions);
            supportingEvidenceReferences = supportingEvidenceReferences == null
                    ? List.of() : List.copyOf(supportingEvidenceReferences);
            contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
            missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
        }
    }

    public record AdaptiveCapabilityRecord(
            String capabilityId,
            String capabilityVersion,
            UUID marketId,
            String reason,
            String expectedEvidence,
            String status,
            UUID executionId,
            String diagnostic,
            String interpretationEffect
    ) {
        public AdaptiveCapabilityRecord(
                String capabilityId, String capabilityVersion, UUID marketId, String reason,
                String expectedEvidence, String status, UUID executionId, String diagnostic) {
            this(capabilityId, capabilityVersion, marketId, reason, expectedEvidence, status,
                    executionId, diagnostic, "NOT_REEVALUATED");
        }
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
                String thesisFreshness,
                String evidenceFreshness,
                Instant thesisEvaluatedAt,
                Instant thesisValidFrom,
                Instant thesisValidUntil,
                Instant evidenceEvaluationCutoff,
                Instant evidenceSourceOccurredAt,
                Instant evidenceFetchedAt,
                JsonNode provenance,
            List<JsonNode> supportingEvidence,
            List<JsonNode> contradictingEvidence,
            List<JsonNode> missingEvidence,
            List<String> invalidationConditions,
            String aiStatus,
            JsonNode aiInterpretation,
            List<String> diagnostics,
                JsonNode finalAiInterpretation,
                List<HypothesisReconciliation> reconciliations,
                JsonNode followUpEvidence,
                JsonNode aiProvenance,
                JsonNode finalAiProvenance
    ) {
        public MarketResearchResult {
            supportingEvidence = supportingEvidence == null ? List.of() : List.copyOf(supportingEvidence);
            contradictingEvidence = contradictingEvidence == null ? List.of() : List.copyOf(contradictingEvidence);
            missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
            invalidationConditions = invalidationConditions == null ? List.of() : List.copyOf(invalidationConditions);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
            reconciliations = reconciliations == null ? List.of() : List.copyOf(reconciliations);
        }

        public MarketResearchResult(
                UUID marketId, String provider, String instrument, String productType,
                String status, UUID thesisId, Long thesisVersion, JsonNode deterministicAnalysis,
                String freshness, JsonNode provenance, List<JsonNode> supportingEvidence,
                List<JsonNode> contradictingEvidence, List<JsonNode> missingEvidence,
                List<String> invalidationConditions, String aiStatus, JsonNode aiInterpretation,
                List<String> diagnostics) {
            this(marketId, provider, instrument, productType, status, thesisId, thesisVersion,
                    deterministicAnalysis, freshness, freshness, "UNKNOWN", null, null, null, null, null, null, provenance, supportingEvidence,
                    contradictingEvidence, missingEvidence, invalidationConditions, aiStatus,
                    aiInterpretation, diagnostics, null, List.of(), null, null, null);
        }

        public MarketResearchResult(
                UUID marketId, String provider, String instrument, String productType,
                String status, UUID thesisId, Long thesisVersion, JsonNode deterministicAnalysis,
                String freshness, JsonNode provenance, List<JsonNode> supportingEvidence,
                List<JsonNode> contradictingEvidence, List<JsonNode> missingEvidence,
                List<String> invalidationConditions, String aiStatus, JsonNode aiInterpretation,
                List<String> diagnostics, JsonNode finalAiInterpretation,
                List<HypothesisReconciliation> reconciliations, JsonNode followUpEvidence) {
            this(marketId, provider, instrument, productType, status, thesisId, thesisVersion,
                    deterministicAnalysis, freshness, freshness, "UNKNOWN", null, null, null, null, null, null,
                    provenance, supportingEvidence, contradictingEvidence, missingEvidence,
                    invalidationConditions, aiStatus, aiInterpretation, diagnostics,
                    finalAiInterpretation, reconciliations, followUpEvidence, null, null);
        }
    }

    public record HypothesisReconciliation(
            String hypothesisId,
            String hypothesis,
            String effect,
            String explanation,
            List<UUID> evidenceReferences
    ) {
        public HypothesisReconciliation {
            evidenceReferences = List.copyOf(evidenceReferences);
        }
    }
}
