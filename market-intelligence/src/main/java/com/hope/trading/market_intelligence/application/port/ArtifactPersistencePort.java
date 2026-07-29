package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.capability.*;

import java.util.*;

public interface ArtifactPersistencePort {
    List<ProducedArtifact> find(
            UUID analysisExecutionId, ArtifactType type, ArtifactVersion version);
    void save(UUID analysisExecutionId, ProducedArtifact artifact);
}
