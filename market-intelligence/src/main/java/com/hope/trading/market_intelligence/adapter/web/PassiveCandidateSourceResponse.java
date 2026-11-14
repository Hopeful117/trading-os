package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.scan.PassiveCandidateProvenance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable passive source of an ActiveScan (Story 0243). Contains references and
 * assessment codes only; no evidence payloads, credentials or account data.
 */
public record PassiveCandidateSourceResponse(
        UUID opportunityId,
        long opportunityVersion,
        UUID strategyMatchId,
        List<UUID> observationIds,
        UUID thesisId,
        long thesisVersion,
        UUID passiveScanRunId,
        UUID passiveScanMarketId,
        UUID passiveAnalysisExecutionId,
        UUID marketId,
        String provider,
        String productType,
        Instant opportunityEvaluatedAt,
        Instant thesisSourceCutoff,
        Instant validUntil,
        Instant validatedAt,
        List<EvidenceAssessmentResponse> evidenceAssessment
) {
    public PassiveCandidateSourceResponse {
        observationIds = List.copyOf(observationIds);
        evidenceAssessment = List.copyOf(evidenceAssessment);
    }

    static PassiveCandidateSourceResponse from(PassiveCandidateProvenance value) {
        if (value == null) {
            return null;
        }
        return new PassiveCandidateSourceResponse(
                value.opportunityId(), value.opportunityVersion(), value.strategyMatchId(),
                value.observationIds(), value.thesisId(), value.thesisVersion(), value.passiveScanRunId(),
                value.passiveScanMarketId(), value.passiveAnalysisExecutionId(), value.marketId(),
                value.provider(), value.productType(), value.opportunityEvaluatedAt(),
                value.thesisSourceCutoff(), value.validUntil(), value.validatedAt(),
                value.evidenceAssessment().stream()
                        .map(item -> new EvidenceAssessmentResponse(
                                item.dimension().name(), item.status().name(), item.reason()))
                        .toList());
    }

    public record EvidenceAssessmentResponse(String dimension, String status, String reason) {
    }
}
