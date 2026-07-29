package com.hope.trading.market_intelligence.domain.artifact;

import java.time.Instant;
import java.util.*;

public record ArtifactProvenance(
        String producerId,
        String producerVersion,
        UUID producingExecutionId,
        Instant producedAt,
        Set<ArtifactIdentity> inputArtifacts,
        Set<UUID> reusedByExecutions
) {
    public ArtifactProvenance {
        Objects.requireNonNull(producerId);
        Objects.requireNonNull(producerVersion);
        Objects.requireNonNull(producedAt);
        inputArtifacts = Set.copyOf(inputArtifacts);
        reusedByExecutions = Set.copyOf(reusedByExecutions);
    }

    public ArtifactProvenance reusedBy(UUID executionId) {
        Set<UUID> executions = new HashSet<>(reusedByExecutions);
        executions.add(executionId);
        return new ArtifactProvenance(
                producerId, producerVersion, producingExecutionId, producedAt,
                inputArtifacts, executions
        );
    }
}
