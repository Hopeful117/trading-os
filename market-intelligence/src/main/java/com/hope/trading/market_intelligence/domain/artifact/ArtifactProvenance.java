package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;

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
        String evidenceFingerprint,
        EvidenceAcquisitionPolicy evidencePolicy,
        Integer observationCount,
        Instant evaluationCutoff
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
                inputArtifacts, executions, contextFingerprint, evidenceFingerprint,
                evidencePolicy, observationCount, evaluationCutoff
        );
    }

    public ArtifactProvenance withContextFingerprint(String fingerprint) {
        return new ArtifactProvenance(
                producerId, producerVersion, producingExecutionId, producedAt,
                inputArtifacts, reusedByExecutions, fingerprint, evidenceFingerprint,
                evidencePolicy, observationCount, evaluationCutoff);
    }

    public ArtifactProvenance withEvidenceFingerprint(String fingerprint) {
        return new ArtifactProvenance(
                producerId, producerVersion, producingExecutionId, producedAt,
                inputArtifacts, reusedByExecutions, contextFingerprint, fingerprint,
                evidencePolicy, observationCount, evaluationCutoff);
    }

    public ArtifactProvenance(
            String producerId,
            String producerVersion,
            UUID producingExecutionId,
            Instant producedAt,
            Set<ArtifactIdentity> inputArtifacts,
            Set<UUID> reusedByExecutions,
            String contextFingerprint,
            String evidenceFingerprint
    ) {
        this(producerId, producerVersion, producingExecutionId, producedAt,
                inputArtifacts, reusedByExecutions, contextFingerprint, evidenceFingerprint,
                null, null, null);
    }

    public ArtifactProvenance withEvidenceContext(
            EvidenceAcquisitionPolicy policy, Integer observationCount, Instant cutoff) {
        return new ArtifactProvenance(
                producerId, producerVersion, producingExecutionId, producedAt,
                inputArtifacts, reusedByExecutions, contextFingerprint, evidenceFingerprint,
                policy, observationCount, cutoff);
    }
}
