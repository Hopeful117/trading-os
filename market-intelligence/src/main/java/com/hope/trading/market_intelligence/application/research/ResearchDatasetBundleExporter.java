package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.research.DatasetManifest;
import com.hope.trading.market_intelligence.domain.research.ResearchFeatureSnapshotKey;
import com.hope.trading.market_intelligence.domain.research.ResearchOutcomeKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ResearchDatasetBundleExporter {
    public static final String MANIFEST_FILE = "dataset-manifest.json";
    public static final String SAMPLES_FILE = "samples.jsonl";
    public static final String README_FILE = "README.md";
    public static final String EXPORT_SCHEMA_VERSION = "research-dataset-bundle-v1";

    private final ObjectMapper mapper;
    private final ResearchDatasetJsonlExporter samplesExporter;

    public ResearchDatasetBundleExporter(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "object mapper");
        this.samplesExporter = new ResearchDatasetJsonlExporter(mapper);
    }

    public void export(DatasetBuildResult dataset, Path outputDirectory) {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(outputDirectory, "output directory");
        if (Files.exists(outputDirectory)) {
            throw new IllegalArgumentException("export directory already exists: " + outputDirectory);
        }
        Path parent = outputDirectory.toAbsolutePath().getParent();
        if (parent == null) throw new IllegalArgumentException("export directory must have a parent");
        try {
            Files.createDirectories(parent);
            Path temporary = Files.createTempDirectory(parent, outputDirectory.getFileName() + ".tmp-");
            exportTemporary(dataset, outputDirectory, temporary);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot export research dataset bundle", exception);
        }
    }

    private void exportTemporary(DatasetBuildResult dataset, Path outputDirectory, Path temporary)
            throws IOException {
        try {
            String samples = samplesExporter.export(dataset);
            write(temporary.resolve(SAMPLES_FILE), samples);
            String samplesFingerprint = sha256(samples.getBytes(StandardCharsets.UTF_8));
            write(temporary.resolve(MANIFEST_FILE), manifest(dataset.manifest(), samplesFingerprint));
            write(temporary.resolve(README_FILE), readme(dataset.manifest()));
            Files.move(temporary, outputDirectory, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception exception) {
            deleteRecursively(temporary);
            throw exception;
        }
    }

    private String manifest(DatasetManifest value, String samplesFingerprint) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("exportSchemaVersion", EXPORT_SCHEMA_VERSION);
        document.put("datasetDefinitionVersion", value.datasetDefinitionVersion());
        document.put("population", population(value));
        document.put("featureSchema", mapper.valueToTree(value.featureSchema()));
        document.put("outcomeSchema", value.outcomeSchema());
        document.put("sampleCount", value.sampleCount());
        document.put("eligibleCount", value.eligibleCount());
        document.put("excludedCount", value.excludedCount());
        Map<String, Integer> exclusions = new LinkedHashMap<>();
        value.exclusionCounts().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> exclusions.put(entry.getKey().name(), entry.getValue()));
        document.put("exclusionCounts", exclusions);
        document.put("datasetFingerprint", value.datasetFingerprint());
        document.put("fingerprintMaterial", value.fingerprintMaterial());
        document.put("samplesSha256", samplesFingerprint);
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n";
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize research dataset manifest", exception);
        }
    }

    private Map<String, Object> population(DatasetManifest manifest) {
        var value = manifest.population();
        Map<String, Object> population = new LinkedHashMap<>();
        population.put("populationPolicyVersion", value.populationPolicyVersion());
        population.put("marketIds", value.marketIds().stream().map(Object::toString).sorted().toList());
        population.put("startCutoffInclusive", value.startCutoffInclusive().toString());
        population.put("endCutoffExclusive", value.endCutoffExclusive().toString());
        population.put("featureProjectionVersion", value.featureProjectionVersion());
        population.put("featureEvidencePolicy", value.featureEvidencePolicy().name());
        population.put("outcomeDefinitionVersion", value.outcomeDefinitionVersion());
        population.put("horizons", value.horizons().stream().map(horizon -> horizon.value().toString()).sorted().toList());
        population.put("samplingCadence", value.samplingCadence().name());
        population.put("acceptedFeatureMissingness", value.acceptedFeatureMissingness().stream()
                .map(Enum::name).sorted().toList());
        Map<String, Object> featureRequirements = new LinkedHashMap<>();
        featureRequirements.put("version", value.featureRequirements().version());
        featureRequirements.put("requiredFeatureNames", value.featureRequirements().requiredFeatureNames().stream().sorted().toList());
        featureRequirements.put("acceptedMissingness", value.featureRequirements().acceptedMissingness().stream()
                .map(Enum::name).sorted().toList());
        population.put("featureRequirements", featureRequirements);
        List<Map<String, String>> snapshotSelections = new ArrayList<>();
        value.selectedFeatureSnapshotFingerprints().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResearchFeatureSnapshotKey::marketId)
                        .thenComparing(ResearchFeatureSnapshotKey::evaluationCutoff)))
                .forEach(entry -> {
                    Map<String, String> selection = new LinkedHashMap<>();
                    selection.put("marketId", entry.getKey().marketId().toString());
                    selection.put("evaluationCutoff", entry.getKey().evaluationCutoff().toString());
                    selection.put("snapshotFingerprint", entry.getValue());
                    snapshotSelections.add(selection);
                });
        population.put("selectedFeatureSnapshotFingerprints", snapshotSelections);
        List<Map<String, String>> selections = new ArrayList<>();
        value.selectedOutcomeFingerprints().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResearchOutcomeKey::marketId)
                        .thenComparing(ResearchOutcomeKey::evaluationCutoff)
                        .thenComparing(key -> key.horizon().value())))
                .forEach(entry -> {
                    Map<String, String> selection = new LinkedHashMap<>();
                    selection.put("marketId", entry.getKey().marketId().toString());
                    selection.put("evaluationCutoff", entry.getKey().evaluationCutoff().toString());
                    selection.put("horizon", entry.getKey().horizon().value().toString());
                    selection.put("outcomeFingerprint", entry.getValue());
                    selections.add(selection);
                });
        population.put("selectedOutcomeFingerprints", selections);
        return population;
    }

    private String readme(DatasetManifest manifest) {
        return "# Research Dataset Bundle\n\n"
                + "Generated by Market Intelligence export schema `" + EXPORT_SCHEMA_VERSION + "`.\n\n"
                + "The Java dataset builder is authoritative for sample eligibility, missingness, outcomes, and fingerprints.\n"
                + "Validate this directory offline before using it for research. Validation is fail-closed.\n\n"
                + "Dataset fingerprint: `" + manifest.datasetFingerprint() + "`\n";
    }

    private void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(value -> {
                try {
                    Files.deleteIfExists(value);
                } catch (IOException exception) {
                    throw new IllegalStateException("Cannot clean temporary export", exception);
                }
            });
        }
    }
}
