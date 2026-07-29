package com.hope.trading.market_intelligence.domain.artifact;

import java.util.Objects;

public record ArtifactIdentity(
        String artifactType,
        String producerId,
        String producerVersion
) {
    public ArtifactIdentity {
        artifactType = required(artifactType, "artifactType");
        producerId = required(producerId, "producerId");
        producerVersion = required(producerVersion, "producerVersion");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        return normalized;
    }
}
