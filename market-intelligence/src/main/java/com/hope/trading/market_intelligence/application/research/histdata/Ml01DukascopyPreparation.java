package com.hope.trading.market_intelligence.application.research.histdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.HistoricalOhlcContext;
import com.hope.trading.market_intelligence.domain.OhlcPoint;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.volatility.VolatilityAssessment;
import com.hope.trading.market_intelligence.domain.volatility.VolatilityCalculator;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Ml01DukascopyPreparation {
    public static final String POLICY_ID = "ML01_DUKASCOPY_EXPLORATORY_V1";
    public static final String POLICY_VERSION = "v1";
    public static final String FEATURE_SCHEMA_VERSION = "ml01-research-features-v2";
    public static final String OUTCOME_DEFINITION_VERSION = "ml01-future-ohlc-v1";
    public static final String CONTINUITY_POLICY_ID = "ML01_DUKASCOPY_CONTIGUOUS_ONLY_V1";
    public static final String CONTINUITY_POLICY_VERSION = "v1";
    public static final List<Integer> HORIZONS = List.of(4, 12, 24);
    public static final List<String> FEATURES = List.of(
            "ohlc.normalized_atr", "ohlc.realized_volatility",
            "ohlc.volatility_compression_ratio", "ohlc.directional_efficiency");
    private static final MathContext MC = MathContext.DECIMAL128;

    private Ml01DukascopyPreparation() {
    }

    public record Outcome(int horizon, String status, BigDecimal realizedVolatility,
                          BigDecimal directionalEfficiency, BigDecimal absoluteFutureNetReturn,
                          String reason, String fingerprint) {
    }

    public record Row(DukascopyH1Bucket bucket, Ml01DukascopyH1AdmissibilityDecision decision,
                      VolatilityAssessment features, Map<Integer, Outcome> outcomes,
                      boolean featureEligible, boolean jointAvailable, boolean jointEligible, String continuityStratum,
                      String transitionProvenance, String exclusionReason,
                      String rowFingerprint) {
        public Row {
            outcomes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
        }
    }

    public record PreparationResult(List<Row> rows, String datasetFingerprint, String readinessStatus,
                                    Map<String, Object> qualityReport) {
        public PreparationResult {
            rows = List.copyOf(rows);
            qualityReport = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(qualityReport));
        }
    }

    public static PreparationResult prepare(List<DukascopyOfflinePipeline.ImportResult> imports,
                                            Instant startInclusive, Instant endExclusive) {
        List<DukascopyH1Bucket> buckets = DukascopyH1ResearchPipeline.aggregate(imports, startInclusive, endExclusive);
        Ml01DukascopyH1AdmissibilityEvaluator evaluator = new Ml01DukascopyH1AdmissibilityEvaluator();
        List<Ml01DukascopyH1AdmissibilityDecision> decisions = buckets.stream().map(evaluator::evaluate).toList();
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < buckets.size(); index++) {
            DukascopyH1Bucket bucket = buckets.get(index);
            var decision = decisions.get(index);
            List<DukascopyH1Bucket> history = decision.eligible()
                    ? contiguousHistory(buckets, decisions, index) : List.of();
            VolatilityAssessment features = decision.eligible() && history.size() == 33
                    ? calculateFeatures(history, bucket) : unavailableFeatures(bucket, history.size());
            Map<Integer, Outcome> outcomes = new LinkedHashMap<>();
            for (int horizon : HORIZONS) outcomes.put(horizon, decision.eligible()
                    ? calculateOutcome(buckets, decisions, index, horizon)
                    : unavailable(horizon, "H1_BUCKET_NOT_ELIGIBLE"));
            boolean featureEligible = features.status() == VolatilityAssessment.Status.AVAILABLE;
            boolean jointAvailable = featureEligible && outcomes.values().stream()
                    .allMatch(value -> "AVAILABLE".equals(value.status()));
            boolean jointEligible = false;
            String exclusionReason = jointAvailable ? "SCIENTIFIC_APPROVAL_REQUIRED" : !featureEligible
                    ? "FEATURE_UNAVAILABLE" : outcomes.values().stream()
                    .filter(value -> !"AVAILABLE".equals(value.status()))
                    .map(Outcome::reason).findFirst().orElse("OUTCOME_UNAVAILABLE");
            String continuity = decision.eligible() && isContiguousTransition(buckets, decisions, index)
                    ? "CONTIGUOUS_ONLY" : "NOT_CONTIGUOUS";
            String transition = index == 0 ? "SCOPE_START" : buckets.get(index - 1).closeTime() + "->"
                    + bucket.openTime() + (isContiguousTransition(buckets, decisions, index) ? ":CONTIGUOUS" : ":GAP_OR_REJECTED");
            String rowFingerprint = ArtifactFingerprint.ofInputs(List.of(POLICY_ID, POLICY_VERSION,
                    bucket.sourceFingerprint(), features.resultFingerprint(), outcomeMaterial(outcomes),
                    continuity, transition,
                    Boolean.toString(jointAvailable), Boolean.toString(jointEligible))).value();
            rows.add(new Row(bucket, decision, features, outcomes, featureEligible, jointAvailable, jointEligible,
                    continuity, transition, exclusionReason, rowFingerprint));
        }
        rows.sort(Comparator.comparing(value -> value.bucket().openTime()));
        List<String> material = new ArrayList<>(List.of(POLICY_ID, POLICY_VERSION, FEATURE_SCHEMA_VERSION,
                OUTCOME_DEFINITION_VERSION, CONTINUITY_POLICY_ID, CONTINUITY_POLICY_VERSION));
        rows.stream().map(Row::rowFingerprint).sorted().forEach(value -> material.add("row=" + value));
        Map<String, Object> quality = qualityReport(imports, buckets, decisions, rows, startInclusive, endExclusive);
        String readiness = rows.stream().noneMatch(Row::jointAvailable)
                ? "INSUFFICIENT_DATA" : "REQUIRES_SCIENTIFIC_APPROVAL";
        quality.put("readinessStatus", readiness);
        quality.put("partitionParameters", "REQUIRES_SCIENTIFIC_APPROVAL");
        quality.put("kmeansExecuted", false);
        quality.put("modelTrainingExecuted", false);
        String configurationFingerprint = ArtifactFingerprint.ofInputs(List.of(POLICY_ID, POLICY_VERSION,
                FEATURE_SCHEMA_VERSION, OUTCOME_DEFINITION_VERSION, CONTINUITY_POLICY_ID,
                CONTINUITY_POLICY_VERSION, startInclusive.toString(), endExclusive.toString())).value();
        String bundleFingerprint = ArtifactFingerprint.ofInputs(List.of(
                DukascopyOfflinePipeline.assemble(imports).datasetFingerprint(), configurationFingerprint,
                rows.stream().map(Row::rowFingerprint).sorted().toList().toString())).value();
        quality.put("configurationFingerprint", configurationFingerprint);
        quality.put("bundleFingerprint", bundleFingerprint);
        return new PreparationResult(rows, ArtifactFingerprint.ofInputs(material).value(), readiness, quality);
    }

    public static void writeBundle(PreparationResult result, Path outputDirectory) throws IOException {
        if (result == null || outputDirectory == null) throw new IllegalArgumentException("result and output are required");
        if (Files.exists(outputDirectory)) {
            try (var files = Files.list(outputDirectory)) {
                if (files.findAny().isPresent()) throw new IllegalArgumentException("output directory must be empty");
            }
        }
        Files.createDirectories(outputDirectory);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        List<Map<String, Object>> observations = result.rows().stream().map(row -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("provider", DukascopyOfflinePipeline.SOURCE_PROVIDER);
            value.put("instrument", row.bucket().symbol());
            value.put("cutoff", row.bucket().closeTime());
            value.put("quality", row.decision().state());
            value.put("admissibilityDecision", row.decision());
            value.put("continuityPolicyId", CONTINUITY_POLICY_ID);
            value.put("continuityStratum", row.continuityStratum());
            value.put("transitionProvenance", row.transitionProvenance());
            value.put("features", row.features());
            value.put("outcomes", row.outcomes());
            value.put("featureEligible", row.featureEligible());
            value.put("jointAvailable", row.jointAvailable());
            value.put("jointEligible", row.jointEligible());
            value.put("exclusionReason", row.exclusionReason());
            value.put("rowFingerprint", row.rowFingerprint());
            return value;
        }).toList();
        Files.writeString(outputDirectory.resolve("observations.jsonl"), observations.stream()
                .map(value -> toJson(mapper, value)).reduce("", (left, right) -> left + right + "\n"));
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("provider", DukascopyOfflinePipeline.SOURCE_PROVIDER);
        manifest.put("priceSide", DukascopyOfflinePipeline.PRICE_SIDE);
        manifest.put("timeframe", "H1");
        manifest.put("admissibilityProfile", POLICY_ID);
        manifest.put("admissibilityPolicyVersion", POLICY_VERSION);
        manifest.put("continuityPolicyId", CONTINUITY_POLICY_ID);
        manifest.put("continuityPolicyVersion", CONTINUITY_POLICY_VERSION);
        manifest.put("featureSchemaVersion", FEATURE_SCHEMA_VERSION);
        manifest.put("outcomeDefinitionVersion", OUTCOME_DEFINITION_VERSION);
        manifest.put("features", FEATURES);
        manifest.put("horizons", HORIZONS);
        manifest.put("rowCount", result.rows().size());
        manifest.put("jointAvailableCount", result.rows().stream().filter(Row::jointAvailable).count());
        manifest.put("jointEligibleCount", result.rows().stream().filter(Row::jointEligible).count());
        manifest.put("datasetFingerprint", result.datasetFingerprint());
        manifest.put("configurationFingerprint", result.qualityReport().get("configurationFingerprint"));
        manifest.put("bundleFingerprint", result.qualityReport().get("bundleFingerprint"));
        manifest.put("researchBounds", Map.of("startInclusive", result.qualityReport().get("startInclusive"),
                "endExclusive", result.qualityReport().get("endExclusive")));
        manifest.put("readinessStatus", result.readinessStatus());
        mapper.writeValue(outputDirectory.resolve("dataset-manifest.json").toFile(), manifest);
        mapper.writeValue(outputDirectory.resolve("quality-report.json").toFile(), result.qualityReport());
        mapper.writeValue(outputDirectory.resolve("readiness.json").toFile(), Map.of(
                "status", result.readinessStatus(), "kmeansExecuted", false,
                "modelTrainingExecuted", false, "requiresScientificApproval", true,
                "configurationFingerprint", result.qualityReport().get("configurationFingerprint"),
                "bundleFingerprint", result.qualityReport().get("bundleFingerprint")));
        mapper.writeValue(outputDirectory.resolve("partition-manifest.json").toFile(),
                Map.of("status", "REQUIRES_SCIENTIFIC_APPROVAL",
                        "configurationFingerprint", result.qualityReport().get("configurationFingerprint")));
        mapper.writeValue(outputDirectory.resolve("source-metadata.json").toFile(), Map.of(
                "provider", DukascopyOfflinePipeline.SOURCE_PROVIDER,
                "priceSide", DukascopyOfflinePipeline.PRICE_SIDE,
                "normalizationVersion", DukascopyOfflinePipeline.NORMALIZATION_VERSION,
                "sourceDatasetFingerprint", result.qualityReport().get("sourceDatasetFingerprint"),
                "sourceFiles", result.qualityReport().get("sourceFiles"),
                "sourceChecksums", result.qualityReport().get("sourceChecksums"),
                "sourceFingerprints", result.qualityReport().get("sourceFingerprints")));
    }

    private static String toJson(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot serialize Dukascopy research row", exception);
        }
    }

    private static List<DukascopyH1Bucket> contiguousHistory(List<DukascopyH1Bucket> buckets,
                                                              List<Ml01DukascopyH1AdmissibilityDecision> decisions,
                                                              int index) {
        if (index < 32) return List.of();
        List<DukascopyH1Bucket> result = new ArrayList<>();
        for (int position = index - 32; position <= index; position++) {
            if (!decisions.get(position).eligible()) return List.of();
            if (position > index - 32
                    && !buckets.get(position - 1).closeTime().equals(buckets.get(position).openTime())) return List.of();
            result.add(buckets.get(position));
        }
        return result;
    }

    private static VolatilityAssessment calculateFeatures(List<DukascopyH1Bucket> history,
                                                          DukascopyH1Bucket reference) {
        List<OhlcPoint> points = history.stream().map(value -> new OhlcPoint(value.openTime(), value.closeTime(),
                value.open(), value.high(), value.low(), value.close(), value.volume())).toList();
        return new VolatilityCalculator().calculate(
                new HistoricalOhlcContext(reference.marketId(), "H1", points), reference.closeTime(),
                ArtifactFingerprint.ofInputs(points.stream().map(Object::toString).toList()).value());
    }

    private static VolatilityAssessment unavailableFeatures(DukascopyH1Bucket reference, int count) {
        return new VolatilityCalculator().calculate(new HistoricalOhlcContext(reference.marketId(), "H1", List.of()),
                reference.closeTime(), "insufficient-history-" + count);
    }

    private static Outcome calculateOutcome(List<DukascopyH1Bucket> buckets,
                                            List<Ml01DukascopyH1AdmissibilityDecision> decisions,
                                            int index, int horizon) {
        if (index + horizon >= buckets.size()) return unavailable(horizon, "INCOMPLETE_FORWARD_WINDOW");
        List<DukascopyH1Bucket> future = new ArrayList<>();
        for (int position = index + 1; position <= index + horizon; position++) {
            if (!decisions.get(position).eligible()
                    || !buckets.get(position - 1).closeTime().equals(buckets.get(position).openTime())) {
                return unavailable(horizon, "NON_CONTIGUOUS_FORWARD_WINDOW");
            }
            future.add(buckets.get(position));
        }
        BigDecimal reference = buckets.get(index).close();
        BigDecimal finalClose = future.getLast().close();
        BigDecimal sumSquares = BigDecimal.ZERO;
        BigDecimal path = BigDecimal.ZERO;
        BigDecimal previous = reference;
        for (DukascopyH1Bucket candle : future) {
            BigDecimal change = candle.close().subtract(previous, MC).divide(previous, MC);
            sumSquares = sumSquares.add(change.multiply(change, MC), MC);
            path = path.add(candle.close().subtract(previous, MC).abs(), MC);
            previous = candle.close();
        }
        BigDecimal efficiency = path.signum() == 0 ? BigDecimal.ZERO
                : finalClose.subtract(reference, MC).abs().divide(path, MC);
        BigDecimal absoluteReturn = finalClose.subtract(reference, MC).divide(reference, MC).abs();
        String fingerprint = ArtifactFingerprint.ofInputs(List.of(OUTCOME_DEFINITION_VERSION,
                reference.toPlainString(), finalClose.toPlainString(), Integer.toString(horizon))).value();
        return new Outcome(horizon, "AVAILABLE", sumSquares.divide(BigDecimal.valueOf(horizon), MC).sqrt(MC),
                efficiency, absoluteReturn, null, fingerprint);
    }

    private static Outcome unavailable(int horizon, String reason) {
        return new Outcome(horizon, "UNAVAILABLE", null, null, null, reason, null);
    }

    private static String outcomeMaterial(Map<Integer, Outcome> outcomes) {
        return outcomes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "|" + entry.getValue().status() + "|"
                        + entry.getValue().fingerprint() + "|" + entry.getValue().reason())
                .reduce((left, right) -> left + ";" + right).orElse("");
    }

    private static boolean isContiguousTransition(List<DukascopyH1Bucket> buckets,
                                                  List<Ml01DukascopyH1AdmissibilityDecision> decisions,
                                                  int index) {
        return index > 0 && decisions.get(index - 1).eligible() && decisions.get(index).eligible()
                && buckets.get(index - 1).closeTime().equals(buckets.get(index).openTime());
    }

    private static Map<String, Object> qualityReport(List<DukascopyOfflinePipeline.ImportResult> imports,
                                                     List<DukascopyH1Bucket> buckets,
                                                     List<Ml01DukascopyH1AdmissibilityDecision> decisions,
                                                     List<Row> rows, Instant startInclusive, Instant endExclusive) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("h1BucketCount", buckets.size());
        result.put("startInclusive", startInclusive);
        result.put("endExclusive", endExclusive);
        result.put("sourceDatasetFingerprint", DukascopyOfflinePipeline.assemble(imports).datasetFingerprint());
        result.put("sourceFiles", imports.stream().map(value -> value.sourceFile().getFileName().toString()).sorted().toList());
        result.put("sourceChecksums", imports.stream().sorted(java.util.Comparator.comparing(
                        value -> value.sourceFile().getFileName().toString()))
                .map(value -> value.sourceFile().getFileName() + "=" + value.sourceFileChecksum()).toList());
        result.put("sourceFingerprints", imports.stream().sorted(java.util.Comparator.comparing(
                        value -> value.sourceFile().getFileName().toString()))
                .map(value -> value.sourceFile().getFileName() + "=" + value.sourceFingerprint()).toList());
        result.put("qualityStateCounts", decisions.stream().collect(java.util.stream.Collectors.groupingBy(
                value -> value.state().name(), LinkedHashMap::new, java.util.stream.Collectors.counting())));
        result.put("featureEligibleCount", rows.stream().filter(Row::featureEligible).count());
        result.put("jointAvailableCount", rows.stream().filter(Row::jointAvailable).count());
        result.put("jointEligibleCount", rows.stream().filter(Row::jointEligible).count());
        result.put("profileSensitivity", sensitivity(buckets));
        result.put("qualityEvidence", buckets.stream().map(value -> Map.of(
                "openTime", value.openTime(), "observedMinutePositions", value.observedMinutePositions(),
                "missingMinutePositions", value.missingMinutePositions(),
                "exactDuplicateRows", value.exactDuplicateRows(),
                "conflictingDuplicateRows", value.conflictingDuplicateRows(),
                "strictState", value.strictState(),
                "strictContinuityClassification", value.strictContinuityClassification())).toList());
        result.put("outcomeAvailabilityByHorizon", HORIZONS.stream().collect(java.util.stream.Collectors.toMap(
                horizon -> Integer.toString(horizon), horizon -> rows.stream().filter(value ->
                        "AVAILABLE".equals(value.outcomes().get(horizon).status())).count(),
                (left, right) -> left, LinkedHashMap::new)));
        result.put("partialRowsAreResearchOnly", true);
        result.put("scientificApprovalRequiredForEligibility", true);
        result.put("assumedSessionTransitions", false);
        return result;
    }

    private static Map<String, Long> sensitivity(List<DukascopyH1Bucket> buckets) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Ml01DukascopyH1AdmissibilityProfile profile : List.of(
                Ml01DukascopyH1AdmissibilityProfile.V1_60,
                Ml01DukascopyH1AdmissibilityProfile.V1_59,
                Ml01DukascopyH1AdmissibilityProfile.V1)) {
            long count = buckets.stream().map(new Ml01DukascopyH1AdmissibilityEvaluator(profile)::evaluate)
                    .filter(Ml01DukascopyH1AdmissibilityDecision::eligible).count();
            result.put(profile.name(), count);
        }
        return result;
    }
}
