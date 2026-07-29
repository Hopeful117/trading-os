package com.hope.trading.market_intelligence.application.planning;

import com.hope.trading.market_intelligence.domain.capability.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArtifactAdapterRegistry {
    private final Map<Key, ArtifactAdapter> adapters = new ConcurrentHashMap<>();
    public void register(ArtifactAdapter adapter) {
        Key key = new Key(adapter.artifactType(), adapter.sourceVersion(), adapter.targetVersion());
        if (adapters.putIfAbsent(key, adapter) != null)
            throw new IllegalArgumentException("Duplicate artifact adapter " + key);
    }
    public Optional<ArtifactAdapter> find(
            ArtifactType type, ArtifactVersion source, ArtifactVersion target) {
        return Optional.ofNullable(adapters.get(new Key(type, source, target)));
    }
    private record Key(ArtifactType type, ArtifactVersion source, ArtifactVersion target) {}
}
