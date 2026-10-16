package com.hope.trading.market_intelligence.domain.feature;

import java.util.List;

public record AnalyzerProvenance(
        String sourceCapability,
        String sourceVersion,
        String algorithmVersion,
        String ruleVersion,
        String profileId,
        String profileVersion,
        String policyId,
        String policyVersion,
        String parameterFingerprint,
        String inputFingerprint,
        String resultFingerprint,
        String evidenceFingerprint,
        List<String> normalizationVersions,
        String sourceArtifactType,
        String sourceArtifactProducerId,
        String sourceArtifactProducerVersion,
        String sourceArtifactParametersFingerprint,
        String sourceArtifactInputFingerprint,
        String sourceArtifactContractVersion
) {
    public AnalyzerProvenance {
        if (sourceCapability == null || sourceCapability.isBlank()) {
            throw new IllegalArgumentException("sourceCapability is required");
        }
        if (sourceVersion == null || sourceVersion.isBlank()) {
            throw new IllegalArgumentException("sourceVersion is required");
        }
        normalizationVersions = List.copyOf(normalizationVersions == null ? List.of() : normalizationVersions);
        sourceArtifactType = required(sourceArtifactType, "sourceArtifactType");
        sourceArtifactProducerId = required(sourceArtifactProducerId, "sourceArtifactProducerId");
        sourceArtifactProducerVersion = required(sourceArtifactProducerVersion, "sourceArtifactProducerVersion");
        sourceArtifactParametersFingerprint = required(
                sourceArtifactParametersFingerprint, "sourceArtifactParametersFingerprint");
        sourceArtifactInputFingerprint = required(
                sourceArtifactInputFingerprint, "sourceArtifactInputFingerprint");
        sourceArtifactContractVersion = required(
                sourceArtifactContractVersion, "sourceArtifactContractVersion");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
