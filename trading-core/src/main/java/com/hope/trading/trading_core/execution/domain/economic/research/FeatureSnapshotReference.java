package com.hope.trading.trading_core.execution.domain.economic.research;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FeatureSnapshotReference(
        UUID marketId,
        Instant evaluationCutoff,
        String featureProjectionVersion,
        String contextFingerprint,
        String featureSnapshotFingerprint,
        String canonicalIdentityFingerprint
) {
    public FeatureSnapshotReference {
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(evaluationCutoff, "evaluationCutoff is required");
        featureProjectionVersion = required(featureProjectionVersion, "featureProjectionVersion");
        contextFingerprint = required(contextFingerprint, "contextFingerprint");
        featureSnapshotFingerprint = required(featureSnapshotFingerprint, "featureSnapshotFingerprint");
        canonicalIdentityFingerprint = required(canonicalIdentityFingerprint, "canonicalIdentityFingerprint");
    }

    public String identityFingerprint() {
        return canonicalIdentityFingerprint;
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
