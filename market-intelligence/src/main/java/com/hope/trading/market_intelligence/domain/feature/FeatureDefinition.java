package com.hope.trading.market_intelligence.domain.feature;

import java.util.Objects;

public record FeatureDefinition(
        String name,
        FeatureGroup group,
        FeatureDataType dataType,
        FeatureUnit unit,
        String sourceCapability,
        String sourceSemantic,
        int order,
        FeatureMissingnessPolicy missingnessPolicy,
        String description
) {
    public FeatureDefinition {
        name = required(name, "name");
        sourceCapability = required(sourceCapability, "sourceCapability");
        sourceSemantic = required(sourceSemantic, "sourceSemantic");
        description = required(description, "description");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(dataType, "dataType");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(missingnessPolicy, "missingnessPolicy");
        if (order < 0) throw new IllegalArgumentException("order must not be negative");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
