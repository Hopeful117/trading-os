package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.evidencequality.EvidenceQualityAssessment;

import java.util.List;
import java.util.Objects;

public record OpportunityRankingRationale(
        String policyVersion,
        String evidenceQualityPolicyVersion,
        EvidenceQualityAssessment.QualityStatus qualityStatus,
        List<DimensionDecision> dimensions,
        List<Reason> reasons,
        String evidenceQualityFingerprint,
        String thesisInputFingerprint
) {
    public OpportunityRankingRationale {
        policyVersion = required(policyVersion, "policyVersion");
        evidenceQualityPolicyVersion = required(evidenceQualityPolicyVersion,
                "evidenceQualityPolicyVersion");
        Objects.requireNonNull(qualityStatus, "qualityStatus");
        dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        evidenceQualityFingerprint = required(evidenceQualityFingerprint, "evidenceQualityFingerprint");
        thesisInputFingerprint = required(thesisInputFingerprint, "thesisInputFingerprint");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    public record DimensionDecision(
            EvidenceQualityAssessment.Dimension dimension,
            EvidenceQualityAssessment.DimensionState state,
            int precedence
    ) {
        public DimensionDecision {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(state, "state");
        }
    }

    public record Reason(String code, String explanation) {
        public Reason {
            code = required(code, "code");
            explanation = required(explanation, "explanation");
        }
    }
}
