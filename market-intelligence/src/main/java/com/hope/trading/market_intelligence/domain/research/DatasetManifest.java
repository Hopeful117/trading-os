package com.hope.trading.market_intelligence.domain.research;

import com.hope.trading.market_intelligence.domain.feature.FeatureSchema;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;

import java.time.Instant;
import java.util.*;

public record DatasetManifest(
        String datasetDefinitionVersion,
        ResearchPopulationSpec population,
        FeatureSchema featureSchema,
        List<String> outcomeSchema,
        int sampleCount,
        int eligibleCount,
        int excludedCount,
        Map<ResearchExclusionReason, Integer> exclusionCounts,
        Instant createdAt,
        String datasetFingerprint,
        List<String> fingerprintMaterial
) {
    public DatasetManifest {
        datasetDefinitionVersion = required(datasetDefinitionVersion, "dataset definition version");
        Objects.requireNonNull(population, "population");
        Objects.requireNonNull(featureSchema, "feature schema");
        outcomeSchema = List.copyOf(outcomeSchema);
        if (sampleCount < 0 || eligibleCount < 0 || excludedCount < 0 || eligibleCount + excludedCount != sampleCount) {
            throw new IllegalArgumentException("invalid dataset counts");
        }
        exclusionCounts = Map.copyOf(exclusionCounts);
        Objects.requireNonNull(createdAt, "created at");
        datasetFingerprint = required(datasetFingerprint, "dataset fingerprint");
        fingerprintMaterial = List.copyOf(fingerprintMaterial);
        if (fingerprintMaterial.isEmpty()) {
            throw new IllegalArgumentException("fingerprint material must not be empty");
        }
    }

    public Set<OutcomeHorizon> horizons() {
        return population.horizons();
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
