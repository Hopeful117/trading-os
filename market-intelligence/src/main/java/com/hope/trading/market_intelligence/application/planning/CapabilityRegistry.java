package com.hope.trading.market_intelligence.application.planning;

import com.hope.trading.market_intelligence.domain.capability.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CapabilityRegistry {
    private final Map<Key, Capability> capabilities = new ConcurrentHashMap<>();

    public void register(Capability capability) {
        CapabilityMetadata metadata = capability.metadata();
        Key key = new Key(metadata.id(), metadata.version());
        if (capabilities.putIfAbsent(key, capability) != null)
            throw new IllegalArgumentException("Duplicate capability " + key);
    }
    public Optional<Capability> find(CapabilityId id, CapabilityVersion version) {
        return Optional.ofNullable(capabilities.get(new Key(id, version)));
    }
    public List<Capability> all() { return List.copyOf(capabilities.values()); }
    public List<Capability> producers(ArtifactRequirement requirement) {
        return capabilities.values().stream().filter(capability ->
                capability.metadata().producedContributions().stream()
                        .filter(ProducedContribution.ArtifactContribution.class::isInstance)
                        .map(ProducedContribution.ArtifactContribution.class::cast)
                        .anyMatch(produced -> produced.satisfies(requirement))).toList();
    }
    private record Key(CapabilityId id, CapabilityVersion version) {}
}
