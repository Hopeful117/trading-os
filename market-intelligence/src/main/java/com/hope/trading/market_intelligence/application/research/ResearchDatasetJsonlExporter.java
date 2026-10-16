package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.feature.*;
import com.hope.trading.market_intelligence.domain.research.ResearchSample;

import java.util.*;

public final class ResearchDatasetJsonlExporter {
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
        document.put("status", sample.status().name());
        document.put("exclusionReason", sample.exclusionReason() == null ? null : sample.exclusionReason().name());
        document.put("features", features(schema, sample));
        document.put("outcome", outcome(sample));
        Map<String, String> lineage = new LinkedHashMap<>();
        lineage.put("featureSnapshotFingerprint", sample.featureSnapshot().fingerprint());
        lineage.put("featureIdentityFingerprint", sample.featureSnapshot().identityFingerprint());
        lineage.put("contextFingerprint", sample.featureSnapshot().contextFingerprint());
        lineage.put("outcomeFingerprint", sample.outcome() == null ? null : sample.outcome().outcomeFingerprint());
        lineage.put("sampleFingerprint", sample.sampleFingerprint());
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
            projected.put("status", value.status().name());
            projected.put("value", value(value));
            result.put(value.name(), projected);
        }
        return result;
    }

    private Map<String, Object> outcome(ResearchSample sample) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (sample.outcome() == null) {
            result.put("status", null);
            return result;
        }
        var outcome = sample.outcome();
        result.put("status", outcome.status().name());
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

    private Object value(FeatureValue value) {
        return switch (value) {
            case NumericFeatureValue numeric -> numeric.value();
            case CategoricalFeatureValue categorical -> categorical.value();
            case BooleanFeatureValue booleanValue -> booleanValue.value();
        };
    }
}
