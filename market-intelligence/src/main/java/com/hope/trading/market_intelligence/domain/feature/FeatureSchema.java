package com.hope.trading.market_intelligence.domain.feature;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record FeatureSchema(String version, List<FeatureDefinition> definitions) {
    public FeatureSchema {
        version = Objects.requireNonNull(version, "version");
        if (version.isBlank()) throw new IllegalArgumentException("version must not be blank");
        definitions = definitions.stream().sorted(java.util.Comparator.comparingInt(FeatureDefinition::order)).toList();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < definitions.size(); index++) {
            FeatureDefinition definition = Objects.requireNonNull(definitions.get(index));
            if (definition.order() != index) throw new IllegalArgumentException("Feature order must be contiguous");
            if (!names.add(definition.name())) throw new IllegalArgumentException("Duplicate feature: " + definition.name());
        }
    }
}
