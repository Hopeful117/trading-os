package com.hope.trading.market_intelligence.domain.feature;

import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record FeatureSnapshot(
        UUID snapshotId,
        UUID analysisExecutionId,
        UUID marketId,
        Instant evaluationCutoff,
        EvidenceAcquisitionPolicy evidencePolicy,
        String featureProjectionVersion,
        String contextFingerprint,
        List<FeatureValue> values,
        List<FeatureGroupStatus> groups,
        List<AnalyzerProvenance> provenance,
        String fingerprint,
        Instant createdAt
) {
    public FeatureSnapshot {
        Objects.requireNonNull(snapshotId);
        Objects.requireNonNull(analysisExecutionId);
        Objects.requireNonNull(marketId);
        Objects.requireNonNull(evaluationCutoff);
        Objects.requireNonNull(evidencePolicy);
        featureProjectionVersion = required(featureProjectionVersion, "featureProjectionVersion");
        contextFingerprint = required(contextFingerprint, "contextFingerprint");
        fingerprint = required(fingerprint, "fingerprint");
        Objects.requireNonNull(createdAt);
        values = List.copyOf(values);
        groups = List.copyOf(groups);
        provenance = List.copyOf(provenance);
    }

    public boolean hasSameSemanticContent(FeatureSnapshot other) {
        return marketId.equals(other.marketId)
                && evaluationCutoff.equals(other.evaluationCutoff)
                && evidencePolicy == other.evidencePolicy
                && featureProjectionVersion.equals(other.featureProjectionVersion)
                && contextFingerprint.equals(other.contextFingerprint)
                && values.equals(other.values)
                && groups.equals(other.groups)
                && provenance.equals(other.provenance)
                && fingerprint.equals(other.fingerprint);
    }

    public String identityFingerprint() {
        List<String> material = new ArrayList<>();
        material.add("market=" + marketId);
        material.add("cutoff=" + evaluationCutoff);
        material.add("policy=" + evidencePolicy);
        material.add("projection=" + featureProjectionVersion);
        material.add("context=" + contextFingerprint);
        provenance.stream()
                .map(AnalyzerProvenance::toString)
                .sorted()
                .forEach(value -> material.add("provenance=" + value));
        return ArtifactFingerprint.ofInputs(material).value();
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
