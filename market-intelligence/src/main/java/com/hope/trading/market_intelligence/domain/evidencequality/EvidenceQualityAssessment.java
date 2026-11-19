package com.hope.trading.market_intelligence.domain.evidencequality;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable, non-probabilistic assessment of the quality of known evidence. */
public record EvidenceQualityAssessment(
        String policyVersion,
        Instant evaluatedAt,
        Instant sourceCutoff,
        QualityStatus status,
        List<DimensionAssessment> dimensions,
        List<QualityReason> reasons,
        List<EvidenceProvenance> provenance,
        String inputFingerprint,
        String resultFingerprint
) {
    public static final String POLICY_VERSION = "evidence-quality/v1";

    public EvidenceQualityAssessment {
        policyVersion = required(policyVersion, "policyVersion");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(sourceCutoff, "sourceCutoff");
        Objects.requireNonNull(status, "status");
        dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        provenance = List.copyOf(Objects.requireNonNull(provenance, "provenance"));
        inputFingerprint = fingerprint(inputFingerprint, "inputFingerprint");
        resultFingerprint = fingerprint(resultFingerprint, "resultFingerprint");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String fingerprint(String value, String field) {
        return new ArtifactFingerprint(required(value, field)).value();
    }

    public enum QualityStatus {
        SUPPORTED,
        DEGRADED,
        BLOCKED,
        UNAVAILABLE
    }

    public enum Dimension {
        COMPLETENESS,
        FRESHNESS,
        PROVENANCE,
        CONTRADICTION,
        APPLICABILITY,
        MISSING_POLICY
    }

    public enum DimensionState {
        SATISFIED,
        PARTIAL,
        MISSING,
        STALE,
        CONTRADICTED,
        UNAVAILABLE,
        NOT_IMPLEMENTED,
        NOT_APPLICABLE
    }

    public record DimensionAssessment(
            Dimension dimension,
            DimensionState state,
            List<String> evidenceReferences
    ) {
        public DimensionAssessment {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(state, "state");
            evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences,
                    "evidenceReferences"));
        }
    }

    public record QualityReason(String code, String explanation) {
        public QualityReason {
            code = required(code, "code");
            explanation = required(explanation, "explanation");
        }
    }

    public record EvidenceProvenance(
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
        public EvidenceProvenance {
            evidenceId = required(evidenceId, "evidenceId");
            source = required(source, "source");
            Objects.requireNonNull(observedAt, "observedAt");
        }
    }
}
