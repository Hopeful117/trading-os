package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.feature.*;
import com.hope.trading.market_intelligence.domain.research.ResearchSample;

import java.util.*;

public final class ResearchDatasetJsonlExporter {
    private static final String STATUS = "status";
    private final ObjectMapper mapper;

    public ResearchDatasetJsonlExporter(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "object mapper");
    }

    public String export(DatasetBuildResult dataset) {
        Objects.requireNonNull(dataset, "dataset");
        return dataset.samples().stream().map(sample -> line(dataset.manifest().featureSchema(), sample))
                .collect(java.util.stream.Collectors.joining("\n"))
                + (dataset.samples().isEmpty() ? "" : "\n");
    }

    private String line(FeatureSchema schema, ResearchSample sample) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("sampleId", sample.sampleId().toString());
        document.put("marketId", sample.marketId().toString());
        document.put("evaluationCutoff", sample.evaluationCutoff().toString());
        document.put("horizon", sample.horizon().value().toString());
        document.put("featureProjectionVersion", sample.featureProjectionVersion());
        document.put("evidencePolicy", sample.featureSnapshot().evidencePolicy().name());
        document.put("featureSnapshotCreatedAt", sample.featureSnapshot().createdAt().toString());
        document.put(STATUS, sample.status().name());
        document.put("exclusionReason", sample.exclusionReason() == null ? null : sample.exclusionReason().name());
        document.put("features", features(schema, sample));
        document.put("outcome", outcome(sample));
        document.put("featureProvenance", sample.featureSnapshot().provenance());
        Map<String, Object> lineage = new LinkedHashMap<>();
        lineage.put("featureSnapshotFingerprint", sample.featureSnapshot().fingerprint());
        lineage.put("featureIdentityFingerprint", sample.featureSnapshot().identityFingerprint());
        lineage.put("contextFingerprint", sample.featureSnapshot().contextFingerprint());
        lineage.put("outcomeFingerprint", sample.outcome() == null ? null : sample.outcome().outcomeFingerprint());
        lineage.put("sampleFingerprint", sample.sampleFingerprint());
        lineage.put("sampleFingerprintMaterial", sampleFingerprintMaterial(sample));
        document.put("lineage", lineage);
        try {
            return mapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize research sample", exception);
        }
    }

    private Map<String, Object> features(com.hope.trading.market_intelligence.domain.feature.FeatureSchema schema,
                                         ResearchSample sample) {
        Map<String, FeatureValue> values = sample.featureSnapshot().values().stream()
                .collect(java.util.stream.Collectors.toMap(FeatureValue::name, value -> value, (left, right) -> {
                    throw new IllegalStateException("duplicate feature name: " + left.name());
                }, LinkedHashMap::new));
        Map<String, Object> result = new LinkedHashMap<>();
        for (FeatureDefinition definition : schema.definitions()) {
            String name = definition.name();
            FeatureValue value = values.get(name);
            if (value == null) {
                throw new IllegalStateException("sample does not conform to feature schema: " + name);
            }
            Map<String, Object> projected = new LinkedHashMap<>();
            projected.put("type", type(value));
            projected.put(STATUS, value.status().name());
            projected.put("value", value(value));
            result.put(value.name(), projected);
        }
        return result;
    }

    private Map<String, Object> outcome(ResearchSample sample) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (sample.outcome() == null) {
            result.put(STATUS, null);
            return result;
        }
        var outcome = sample.outcome();
        result.put(STATUS, outcome.status().name());
        result.put("maturityAt", outcome.maturityAt().toString());
        result.put("computedAt", outcome.computedAt().toString());
        result.put("outcomeDefinitionVersion", outcome.outcomeDefinitionVersion());
        result.put("referenceObservationFingerprint", outcome.referenceObservationFingerprint());
        result.put("futureEvidenceFingerprint", outcome.futureEvidenceFingerprint());
        result.put("referencePrice", outcome.referencePrice());
        result.put("finalPrice", outcome.finalPrice());
        result.put("futureHigh", outcome.futureHigh());
        result.put("futureLow", outcome.futureLow());
        result.put("forwardReturn", outcome.forwardReturn());
        result.put("futureHighReturn", outcome.futureHighReturn());
        result.put("futureLowReturn", outcome.futureLowReturn());
        result.put("futureRange", outcome.futureRange());
        return result;
    }

    private String type(FeatureValue value) {
        return switch (value) {
            case NumericFeatureValue ignored -> "NUMERIC";
            case CategoricalFeatureValue ignored -> "CATEGORICAL";
            case BooleanFeatureValue ignored -> "BOOLEAN";
        };
    }

    private List<String> sampleFingerprintMaterial(ResearchSample sample) {
        if (sample.outcome() == null) {
            return List.of(
                    "definition=" + ResearchDatasetBuilder.DATASET_DEFINITION_VERSION,
                    "market=" + sample.marketId(),
                    "cutoff=" + sample.evaluationCutoff(),
                    "horizon=" + sample.horizon().value(),
                    "feature=" + sample.featureSnapshot().fingerprint(),
                    "reason=" + sample.exclusionReason());
        }
        return List.of(
                "definition=" + ResearchDatasetBuilder.DATASET_DEFINITION_VERSION,
                "market=" + sample.marketId(),
                "cutoff=" + sample.evaluationCutoff(),
                "horizon=" + sample.horizon().value(),
                "projection=" + sample.featureProjectionVersion(),
                "feature=" + sample.featureSnapshot().fingerprint(),
                "outcome=" + sample.outcome().outcomeFingerprint(),
                "status=" + sample.status(),
                "reason=" + java.util.Objects.toString(sample.exclusionReason(), "null"));
    }

    private Object value(FeatureValue value) {
        return switch (value) {
            case NumericFeatureValue numeric -> numeric.value();
            case CategoricalFeatureValue categorical -> categorical.value();
            case BooleanFeatureValue booleanValue -> booleanValue.value();
        };
    }
}
