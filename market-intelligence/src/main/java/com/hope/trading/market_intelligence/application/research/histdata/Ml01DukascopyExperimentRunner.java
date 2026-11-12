package com.hope.trading.market_intelligence.application.research.histdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Offline, fail-closed entry point for the bounded ML-01.1 Dukascopy experiment. */
public final class Ml01DukascopyExperimentRunner {
    private static final String EXPECTED_DATASET_FINGERPRINT =
            "c212d83c0050374400af8e8c3d9961a2bbcf0f9169fd2c149c0513fc3002f198";
    private static final String EXPECTED_MANIFEST_SHA256 =
            "4542e9e2ab7114edf8ca9514c146905fba8390a0753f26ba4cca47285b752041";
    private static final Instant START = Instant.parse("2025-10-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-10-01T00:00:00Z");
    private static final Map<String, Integer> EXPECTED_RECORDS = Map.of("eurusd", 371_614, "gbpusd", 371_266);

    private Ml01DukascopyExperimentRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path sourceRoot = args.length > 0 ? Path.of(args[0]) : Path.of("histData/dukascopy-20251001-20261001");
        Path outputRoot = args.length > 1 ? Path.of(args[1]) : sourceRoot.resolve("derived-v4-ml01.1-20261112");
        validateSource(sourceRoot);
        Files.createDirectories(outputRoot);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Ml01DukascopyH1AdmissibilityProfile profile : List.of(
                Ml01DukascopyH1AdmissibilityProfile.V1_60,
                Ml01DukascopyH1AdmissibilityProfile.V1_59,
                Ml01DukascopyH1AdmissibilityProfile.V1)) {
            List<Ml01DukascopyPreparation.PreparationResult> prepared = new ArrayList<>();
            for (String symbol : EXPECTED_RECORDS.keySet().stream().sorted().toList()) {
                List<DukascopyOfflinePipeline.ImportResult> imports = readMonthly(sourceRoot, symbol);
                int observations = imports.stream().mapToInt(value -> value.observations().size()).sum();
                if (observations != EXPECTED_RECORDS.get(symbol)) {
                    throw new IllegalStateException(symbol + " observation count mismatch: " + observations);
                }
                prepared.add(Ml01DukascopyPreparation.prepare(imports, START, END, profile));
            }
            for (String scenarioName : List.of("COMMON_UTC_70_15_15", "COMMON_UTC_60_20_20")) {
                for (Duration embargo : List.of(Duration.ZERO, Duration.ofHours(24))) {
                    Ml01DukascopyExperimentConfiguration configuration =
                            Ml01DukascopyExperimentConfiguration.of(scenarioName, START, END, embargo,
                                    profile.name(),
                                    EXPECTED_DATASET_FINGERPRINT);
                    Path bundle = outputRoot.resolve("pooled").resolve(profile.name().toLowerCase())
                            .resolve(scenarioName.toLowerCase() + "-" + (embargo.isZero() ? "none" : "24h"));
                    Instant started = Instant.now();
                    Ml01DukascopyPreparation.writePooledPartitionedBundle(prepared, configuration, bundle);
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("profile", profile.name());
                    summary.put("scenario", scenarioName);
                    summary.put("embargo", embargo);
                    summary.put("configurationFingerprint", configuration.fingerprint());
                    summary.put("bundlePath", bundle.toString());
                    summary.put("durationMillis", Duration.between(started, Instant.now()).toMillis());
                    summaries.add(summary);
                    System.out.printf("profile=%s scenario=%s embargo=%s durationMillis=%d bundle=%s%n",
                            profile.name(), scenarioName, embargo, summary.get("durationMillis"), bundle);
                }
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("experiment", "ML-01.1_DUKASCOPY_SCIENTIFIC_VALIDATION_V1");
        summary.put("provider", "DUKASCOPY");
        summary.put("priceSide", "BID");
        summary.put("startInclusive", START);
        summary.put("endExclusive", END);
        summary.put("sourceDatasetFingerprint", EXPECTED_DATASET_FINGERPRINT);
        summary.put("sourceManifestSha256", EXPECTED_MANIFEST_SHA256);
        summary.put("instruments", summaries);
        summary.put("kmeansExecuted", false);
        summary.put("modelTrainingExecuted", false);
        mapper.writeValue(outputRoot.resolve("experiment-summary.json").toFile(), summary);
    }

    private static List<DukascopyOfflinePipeline.ImportResult> readMonthly(Path sourceRoot, String symbol)
            throws Exception {
        UUID marketId = UUID.nameUUIDFromBytes(("dukascopy:market:" + symbol).getBytes(StandardCharsets.UTF_8));
        List<DukascopyOfflinePipeline.ImportResult> imports = new ArrayList<>();
        for (YearMonth month = YearMonth.of(2025, 10); month.isBefore(YearMonth.of(2026, 10)); month = month.plusMonths(1)) {
            String monthName = month.toString();
            Path source = sourceRoot.resolve("normalized").resolve(symbol).resolve(monthName + ".csv");
            if (!Files.isRegularFile(source)) throw new IllegalStateException("missing source file: " + source);
            imports.add(DukascopyOfflinePipeline.read(source, marketId, symbol.toUpperCase(), START, END));
        }
        return imports;
    }

    private static void validateSource(Path sourceRoot) throws Exception {
        Path manifest = sourceRoot.resolve("acquisition-manifest.json");
        if (!Files.isRegularFile(manifest)) throw new IllegalStateException("missing acquisition manifest: " + manifest);
        String manifestSha = sha256(manifest);
        if (!EXPECTED_MANIFEST_SHA256.equals(manifestSha)) {
            throw new IllegalStateException("acquisition manifest checksum mismatch: " + manifestSha);
        }
        JsonNode root = new ObjectMapper().readTree(manifest.toFile());
        require(root.path("provider").asText(), "DUKASCOPY", "provider");
        require(root.path("timeframe").asText(), "M1", "timeframe");
        require(root.path("priceType").asText(), "bid", "priceType");
        require(root.path("startInclusive").asText(), "2025-10-01T00:00:00.000Z", "startInclusive");
        require(root.path("endExclusive").asText(), "2026-10-01T00:00:00.000Z", "endExclusive");
        require(root.path("datasetFingerprint").asText(), EXPECTED_DATASET_FINGERPRINT, "datasetFingerprint");
        if (!root.path("complete").asBoolean(false)) throw new IllegalStateException("source manifest is incomplete");
    }

    private static void require(String actual, String expected, String field) {
        if (!expected.equals(actual)) throw new IllegalStateException(field + " mismatch: " + actual);
    }

    private static String sha256(Path file) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file)));
    }
}
