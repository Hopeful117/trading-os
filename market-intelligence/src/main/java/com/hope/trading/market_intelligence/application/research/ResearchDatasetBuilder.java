package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.domain.research.*;
import com.hope.trading.market_intelligence.application.feature.FeatureProjectionV1;

import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ResearchDatasetBuilder {
    public static final String DATASET_DEFINITION_VERSION = "research-dataset-v1";

    private final Clock clock;

    public ResearchDatasetBuilder(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DatasetBuildResult build(
            ResearchPopulationSpec population,
            List<FeatureSnapshot> snapshots,
            List<ObservedMarketOutcome> outcomes
    ) {
        Objects.requireNonNull(population, "population");
        var schema = FeatureProjectionV1.schema();
        if (!schema.version().equals(population.featureProjectionVersion())) {
            throw new IllegalArgumentException("unsupported feature projection version: "
                    + population.featureProjectionVersion());
        }
        Map<ResearchOutcomeKey, List<ObservedMarketOutcome>> outcomesByKey = outcomes.stream()
                .collect(Collectors.groupingBy(value -> new ResearchOutcomeKey(
                        value.marketId(), value.evaluationCutoff(), value.horizon())));
        List<ResearchSample> samples = new ArrayList<>();
        Map<String, FeatureSnapshot> seenSnapshots = new HashMap<>();
        for (FeatureSnapshot snapshot : snapshots.stream()
                 .sorted(Comparator.comparing(FeatureSnapshot::marketId).thenComparing(FeatureSnapshot::evaluationCutoff)
                         .thenComparing(FeatureSnapshot::fingerprint)).toList()) {
            if (!population.includes(snapshot.marketId(), snapshot.evaluationCutoff())) continue;
            FeatureSnapshot previous = seenSnapshots.putIfAbsent(snapshot.identityFingerprint(), snapshot);
            boolean duplicate = previous != null;
            if (duplicate && !previous.fingerprint().equals(snapshot.fingerprint())) {
                throw new IllegalArgumentException("conflicting feature snapshots share one semantic identity");
            }
            for (var horizon : population.horizons().stream().sorted(Comparator.comparing(value -> value.value())).toList()) {
                if (duplicate) {
                    samples.add(ResearchSample.excluded(DATASET_DEFINITION_VERSION, snapshot, horizon,
                            ResearchExclusionReason.DUPLICATE_FEATURE_SNAPSHOT));
                } else {
                    samples.add(buildSample(population, snapshot, horizon, outcomesByKey, schema));
                }
            }
        }
        samples.sort(Comparator.comparing(ResearchSample::marketId)
                .thenComparing(ResearchSample::evaluationCutoff)
                .thenComparing(value -> value.horizon().value())
                .thenComparing(ResearchSample::sampleFingerprint));
        Map<ResearchExclusionReason, Integer> exclusions = samples.stream()
                .filter(value -> value.status() == ResearchSampleStatus.EXCLUDED)
                .map(ResearchSample::exclusionReason)
                .collect(Collectors.groupingBy(Function.identity(), () -> new EnumMap<>(ResearchExclusionReason.class), Collectors.summingInt(value -> 1)));
        List<String> fingerprintMaterial = datasetMaterial(population, samples, exclusions);
        String datasetFingerprint = ArtifactFingerprint.ofInputs(fingerprintMaterial).value();
        DatasetManifest manifest = new DatasetManifest(
                DATASET_DEFINITION_VERSION, population, schema,
                List.of("referencePrice", "finalPrice", "futureHigh", "futureLow", "forwardReturn",
                        "futureHighReturn", "futureLowReturn", "futureRange"), samples.size(),
                (int) samples.stream().filter(value -> value.status() == ResearchSampleStatus.ELIGIBLE).count(),
                (int) samples.stream().filter(value -> value.status() == ResearchSampleStatus.EXCLUDED).count(),
                 exclusions, clock.instant(), datasetFingerprint, fingerprintMaterial);
        return new DatasetBuildResult(manifest, samples);
    }

    private ResearchSample buildSample(ResearchPopulationSpec population, FeatureSnapshot snapshot,
                                       com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon horizon,
                                       Map<ResearchOutcomeKey, List<ObservedMarketOutcome>> outcomesByKey,
                                       com.hope.trading.market_intelligence.domain.feature.FeatureSchema schema) {
        if (!snapshot.featureProjectionVersion().equals(population.featureProjectionVersion())
                || snapshot.evidencePolicy() != EvidenceAcquisitionPolicy.STRICT_OBSERVED) {
            return ResearchSample.excluded(DATASET_DEFINITION_VERSION, snapshot, horizon, ResearchExclusionReason.VERSION_MISMATCH);
        }
        if (!matchesSchema(snapshot, schema)) {
            return ResearchSample.excluded(DATASET_DEFINITION_VERSION, snapshot, horizon,
                    ResearchExclusionReason.FEATURE_SNAPSHOT_INVALID);
        }
        Optional<FeatureMissingness> disallowed = snapshot.values().stream()
                .map(com.hope.trading.market_intelligence.domain.feature.FeatureValue::status)
                .filter(status -> status != FeatureMissingness.PRESENT && !population.acceptedFeatureMissingness().contains(status))
                .findFirst();
        if (disallowed.isPresent()) {
            return ResearchSample.excluded(DATASET_DEFINITION_VERSION, snapshot, horizon,
                    ResearchExclusionReason.FEATURE_MISSING_REQUIRED_VALUE);
        }
        ResearchOutcomeKey key = new ResearchOutcomeKey(snapshot.marketId(), snapshot.evaluationCutoff(), horizon);
        String selectedFingerprint = population.selectedOutcomeFingerprint(key).orElse(null);
        if (selectedFingerprint == null) {
            return ResearchSample.excluded(DATASET_DEFINITION_VERSION, snapshot, horizon,
                    ResearchExclusionReason.OUTCOME_VERSION_NOT_SELECTED);
        }
        List<ObservedMarketOutcome> candidates = outcomesByKey.getOrDefault(key, List.of());
        if (candidates.isEmpty()) {
            return ResearchSample.excluded(DATASET_DEFINITION_VERSION, snapshot, horizon,
                    ResearchExclusionReason.OUTCOME_MISSING);
        }
        Optional<ObservedMarketOutcome> selected = candidates.stream()
                .filter(value -> value.outcomeDefinitionVersion().equals(population.outcomeDefinitionVersion()))
                .filter(value -> value.outcomeFingerprint().equals(selectedFingerprint))
                .distinct()
                .reduce((left, right) -> {
                    throw new IllegalArgumentException("conflicting outcomes share one selected identity");
                });
        if (selected.isEmpty()) {
            return ResearchSample.excluded(DATASET_DEFINITION_VERSION, snapshot, horizon,
                    ResearchExclusionReason.OUTCOME_VERSION_MISMATCH);
        }
        ObservedMarketOutcome outcome = selected.get();
        ResearchExclusionReason outcomeReason = switch (outcome.status()) {
            case PENDING -> ResearchExclusionReason.OUTCOME_NOT_MATURE;
            case INCOMPLETE -> ResearchExclusionReason.OUTCOME_INCOMPLETE;
            case UNAVAILABLE -> ResearchExclusionReason.OUTCOME_UNAVAILABLE;
            case AVAILABLE -> null;
        };
        return ResearchSample.create(DATASET_DEFINITION_VERSION, snapshot, outcome,
                outcomeReason == null ? ResearchSampleStatus.ELIGIBLE : ResearchSampleStatus.EXCLUDED, outcomeReason);
    }

    private boolean matchesSchema(FeatureSnapshot snapshot,
                                 com.hope.trading.market_intelligence.domain.feature.FeatureSchema schema) {
        Map<String, com.hope.trading.market_intelligence.domain.feature.FeatureValue> values = new HashMap<>();
        for (var value : snapshot.values()) {
            if (values.put(value.name(), value) != null) return false;
        }
        if (values.size() != schema.definitions().size()) return false;
        return schema.definitions().stream().allMatch(definition -> {
            var value = values.get(definition.name());
            if (value == null) return false;
            return switch (definition.dataType()) {
                case NUMERIC -> value instanceof com.hope.trading.market_intelligence.domain.feature.NumericFeatureValue;
                case CATEGORICAL -> value instanceof com.hope.trading.market_intelligence.domain.feature.CategoricalFeatureValue;
                case BOOLEAN -> value instanceof com.hope.trading.market_intelligence.domain.feature.BooleanFeatureValue;
            };
        });
    }

    private List<String> datasetMaterial(ResearchPopulationSpec population, List<ResearchSample> samples,
                                         Map<ResearchExclusionReason, Integer> exclusions) {
        List<String> material = new ArrayList<>();
        material.add("definition=" + DATASET_DEFINITION_VERSION);
        material.add("populationPolicyVersion=" + population.populationPolicyVersion());
        population.marketIds().stream().map(UUID::toString).sorted()
                .forEach(value -> material.add("market=" + value));
        material.add("startCutoff=" + population.startCutoffInclusive());
        material.add("endCutoff=" + population.endCutoffExclusive());
        material.add("featureProjection=" + population.featureProjectionVersion());
        material.add("featurePolicy=" + population.featureEvidencePolicy());
        material.add("outcomeDefinition=" + population.outcomeDefinitionVersion());
        population.horizons().stream().map(value -> value.value().toString()).sorted()
                .forEach(value -> material.add("horizon=" + value));
        material.add("cadence=" + population.samplingCadence());
        population.acceptedFeatureMissingness().stream().map(Enum::name).sorted()
                .forEach(value -> material.add("acceptedMissingness=" + value));
        population.selectedOutcomeFingerprints().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResearchOutcomeKey::marketId)
                        .thenComparing(ResearchOutcomeKey::evaluationCutoff)
                        .thenComparing(value -> value.horizon().value())))
                .forEach(value -> material.add("selectedOutcome=" + value.getKey() + "|" + value.getValue()));
        samples.stream().map(ResearchSample::sampleFingerprint).sorted().forEach(value -> material.add("sample=" + value));
        exclusions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(value -> material.add("exclusion=" + value.getKey() + "|" + value.getValue()));
        return material;
    }
}
