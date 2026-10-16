package com.hope.trading.market_intelligence.domain.artifact;

import java.time.Instant;
import java.util.*;

public record ArtifactProvenance(
        String producerId,
        String producerVersion,
        UUID producingExecutionId,
        Instant producedAt,
        Set<ArtifactIdentity> inputArtifacts,
        Set<UUID> reusedByExecutions,
        String contextFingerprint,
        String evidenceFingerprint
) {
    public ArtifactProvenance {
        Objects.requireNonNull(producerId);
        Objects.requireNonNull(producerVersion);
        Objects.requireNonNull(producedAt);
        inputArtifacts = Set.copyOf(inputArtifacts);
        reusedByExecutions = Set.copyOf(reusedByExecutions);
    }

    public ArtifactProvenance(
            String producerId,
            String producerVersion,
            UUID producingExecutionId,
            Instant producedAt,
            Set<ArtifactIdentity> inputArtifacts,
            Set<UUID> reusedByExecutions
    ) {
        this(producerId, producerVersion, producingExecutionId, producedAt,
                inputArtifacts, reusedByExecutions, null, null);
    }

    public ArtifactProvenance(
            String producerId,
            String producerVersion,
            UUID producingExecutionId,
            Instant producedAt,
            Set<ArtifactIdentity> inputArtifacts,
            Set<UUID> reusedByExecutions,
            String contextFingerprint
    ) {
        this(producerId, producerVersion, producingExecutionId, producedAt,
                inputArtifacts, reusedByExecutions, contextFingerprint, null);
    }

    public ArtifactProvenance reusedBy(UUID executionId) {
        Set<UUID> executions = new HashSet<>(reusedByExecutions);
        executions.add(executionId);
        return new ArtifactProvenance(
                producerId, producerVersion, producingExecutionId, producedAt,
                inputArtifacts, executions, contextFingerprint, evidenceFingerprint
        );
    }

    public ArtifactProvenance withContextFingerprint(String fingerprint) {
        return new ArtifactProvenance(
                producerId, producerVersion, producingExecutionId, producedAt,
                inputArtifacts, reusedByExecutions, fingerprint, evidenceFingerprint);
    }

    public ArtifactProvenance withEvidenceFingerprint(String fingerprint) {
        return new ArtifactProvenance(
                producerId, producerVersion, producingExecutionId, producedAt,
                inputArtifacts, reusedByExecutions, contextFingerprint, fingerprint);
    }
}
