package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment;

import java.time.Instant;
import java.util.List;

public record EvidenceQualityResponse(
        String policyVersion,
        Instant evaluatedAt,
        Instant sourceCutoff,
        EvidenceQualityAssessment.QualityStatus status,
        List<DimensionResponse> dimensions,
        List<ReasonResponse> reasons,
        List<ProvenanceResponse> provenance,
        String inputFingerprint,
        String resultFingerprint
) {
    public static EvidenceQualityResponse from(EvidenceQualityAssessment assessment) {
        return new EvidenceQualityResponse(
                assessment.policyVersion(), assessment.evaluatedAt(), assessment.sourceCutoff(),
                assessment.status(),
                assessment.dimensions().stream().map(value -> new DimensionResponse(
                        value.dimension(), value.state(), value.evidenceReferences())).toList(),
                assessment.reasons().stream().map(value -> new ReasonResponse(
                        value.code(), value.explanation())).toList(),
                assessment.provenance().stream().map(value -> new ProvenanceResponse(
                        value.evidenceId(), value.source(), value.provider(), value.instrument(),
                        value.timeframe(), value.observedAt(), value.validUntil(), value.sourceCutoff(),
                        value.sourceFingerprint(), value.ruleVersion())).toList(),
                assessment.inputFingerprint(), assessment.resultFingerprint());
    }

    public record DimensionResponse(
            EvidenceQualityAssessment.Dimension dimension,
            EvidenceQualityAssessment.DimensionState state,
            List<String> evidenceReferences
    ) {
    }

    public record ReasonResponse(String code, String explanation) {
    }

    public record ProvenanceResponse(
            String evidenceId,
            String source,
            String provider,
            String instrument,
            String timeframe,
            Instant observedAt,
            Instant validUntil,
            Instant sourceCutoff,
            String sourceFingerprint,
            String ruleVersion
    ) {
    }
}
