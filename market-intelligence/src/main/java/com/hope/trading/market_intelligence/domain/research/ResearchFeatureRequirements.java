package com.hope.trading.market_intelligence.domain.research;

import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;

import java.util.Objects;
import java.util.HashSet;
import java.util.Collection;
import java.util.Set;

public record ResearchFeatureRequirements(
        String version,
        Set<String> requiredFeatureNames,
        Set<FeatureMissingness> acceptedMissingness
) {
    public ResearchFeatureRequirements {
        required(version, "feature requirements version");
        Objects.requireNonNull(requiredFeatureNames, "required feature names");
        requiredFeatureNames = Set.copyOf(canonicalNames(requiredFeatureNames));
        Objects.requireNonNull(acceptedMissingness, "accepted missingness");
        acceptedMissingness = Set.copyOf(acceptedMissingness);
    }

    public static ResearchFeatureRequirements q1() {
        return new ResearchFeatureRequirements("q1-v1", Set.of(), Set.of());
    }

    public static ResearchFeatureRequirements fromNames(
            String version, Collection<String> requiredFeatureNames, Set<FeatureMissingness> acceptedMissingness) {
        return new ResearchFeatureRequirements(version, canonicalNames(requiredFeatureNames), acceptedMissingness);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static Set<String> canonicalNames(Collection<String> names) {
        Objects.requireNonNull(names, "required feature names");
        Set<String> canonicalNames = new HashSet<>();
        for (String value : names) {
            String canonical = required(value, "required feature name").trim();
            if (!canonicalNames.add(canonical)) {
                throw new IllegalArgumentException("duplicate required feature name: " + canonical);
            }
        }
        return canonicalNames;
    }
}
