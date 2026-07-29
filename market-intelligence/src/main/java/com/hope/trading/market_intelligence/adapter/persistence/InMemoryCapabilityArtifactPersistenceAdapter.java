package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.ArtifactPersistencePort;
import com.hope.trading.market_intelligence.domain.capability.*;

import java.util.*;
import java.util.concurrent.*;

public class InMemoryCapabilityArtifactPersistenceAdapter implements ArtifactPersistencePort {
    private final ConcurrentMap<UUID, List<ProducedArtifact>> artifacts = new ConcurrentHashMap<>();
    @Override public List<ProducedArtifact> find(
            UUID analysisId, ArtifactType type, ArtifactVersion version) {
        return artifacts.getOrDefault(analysisId, List.of()).stream()
                .filter(artifact -> artifact.type().equals(type)
                        && artifact.version().equals(version)).toList();
    }
    @Override public void save(UUID analysisId, ProducedArtifact artifact) {
        artifacts.computeIfAbsent(
                analysisId, ignored -> new CopyOnWriteArrayList<>()).add(artifact);
    }
}
