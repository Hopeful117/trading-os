package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.feature.*;
import com.hope.trading.market_intelligence.domain.research.ResearchSample;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;

import java.util.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BooleanSupplier;

public final class ResearchDatasetJsonlExporter {
    private static final String STATUS = "status";
    private final ObjectMapper mapper;
    private final AnalysisExecutionRepository executions;

    public ResearchDatasetJsonlExporter(ObjectMapper mapper) {
        this(mapper, null);
    }

    public ResearchDatasetJsonlExporter(ObjectMapper mapper, AnalysisExecutionRepository executions) {
        this.mapper = Objects.requireNonNull(mapper, "object mapper");
        this.executions = executions;
    }

    public String export(DatasetBuildResult dataset) {
        return export(dataset, () -> true);
    }

    public String export(DatasetBuildResult dataset, BooleanSupplier publicationAllowed) {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(publicationAllowed, "publication guard");
        StringBuilder output = new StringBuilder();
        for (var sample : dataset.samples()) {
            if (!publicationAllowed.getAsBoolean()) {
                throw new IllegalStateException("Research dataset export limit exceeded");
            }
            output.append(line(dataset, sample)).append('\n');
        }
        return output.toString();
    }

    public ExportFile exportTo(Path path, DatasetBuildResult dataset, long maxBytes,
                               BooleanSupplier publicationAllowed) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(publicationAllowed, "publication guard");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            Files.createDirectories(path.toAbsolutePath().getParent());
            try (OutputStream output = new java.security.DigestOutputStream(Files.newOutputStream(path), digest)) {
                for (var sample : dataset.samples()) {
                    if (!publicationAllowed.getAsBoolean()) {
                        throw new IllegalStateException("Research dataset export limit exceeded");
                    }
                    byte[] line = (line(dataset, sample) + "\n").getBytes(StandardCharsets.UTF_8);
                    if (bytes + line.length > maxBytes) {
                        throw new IllegalStateException("Research dataset output exceeds configured byte limit");
                    }
                    output.write(line);
                    bytes += line.length;
                }
            }
            return new ExportFile(bytes, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot stream research dataset samples", exception);
        }
    }

    public record ExportFile(long bytes, String checksum) { }

    private String line(DatasetBuildResult dataset, ResearchSample sample) {
        FeatureSchema schema = dataset.manifest().featureSchema();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("sampleId", sample.sampleId().toString());
        document.put("marketId", sample.marketId().toString());
        document.put("evaluationCutoff", sample.evaluationCutoff().toString());
        document.put("horizon", sample.horizon().value().toString());
        document.put("featureProjectionVersion", sample.featureProjectionVersion());
        document.put("evidencePolicy", sample.featureSnapshot().evidencePolicy().name());
        document.put("featureSnapshotCreatedAt", sample.featureSnapshot().createdAt().toString());
        var knownAt = dataset.featureEvidenceKnownAtBySnapshotFingerprint()
                .get(sample.featureSnapshot().fingerprint());
        if (knownAt != null) document.put("featureEvidenceKnownAt", knownAt.toString());
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
        if (executions != null) {
            var execution = executions.findById(sample.featureSnapshot().analysisExecutionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "feature snapshot execution is missing: "
                                    + sample.featureSnapshot().analysisExecutionId()));
            lineage.put("featureSnapshotAnalysisExecutionId", sample.featureSnapshot().analysisExecutionId());
            lineage.put("executionId", execution.executionId());
            lineage.put("executionOrigin", execution.provenance().executionOrigin().name());
            lineage.put("reconstructionRequestFingerprint",
                    execution.provenance().reconstructionRequestFingerprint());
        }
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
