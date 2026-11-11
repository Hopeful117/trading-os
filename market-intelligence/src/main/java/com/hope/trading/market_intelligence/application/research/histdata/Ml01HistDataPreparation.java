package com.hope.trading.market_intelligence.application.research.histdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.HistoricalOhlcContext;
import com.hope.trading.market_intelligence.domain.OhlcPoint;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.volatility.VolatilityAssessment;
import com.hope.trading.market_intelligence.domain.volatility.VolatilityCalculator;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Database-free ML-01.1 preparation. It deliberately stops before clustering
 * and reports scientific partition approval as a readiness input.
 */
public final class Ml01HistDataPreparation {
    public static final String DATASET_DEFINITION_VERSION = "ml01-h1-dataset-v1";
    public static final String OUTCOME_DEFINITION_VERSION = "ml01-future-ohlc-v1";
    public static final String SOURCE_PROFILE = "histdata-september-2026-h1";
    public static final List<Integer> HORIZONS = List.of(4, 12, 24);
    public static final List<String> FEATURES = List.of(
            "ohlc.normalized_atr",
            "ohlc.realized_volatility",
            "ohlc.volatility_compression_ratio",
            "ohlc.directional_efficiency");
    public static final String RESEARCH_FEATURE_SCHEMA_VERSION = "ml01-research-features-v2";
    public static final String RESEARCH_EXPORT_POLICY = "RESEARCH_ONLY";
    public static final String SOURCE_CHECKSUM_ALGORITHM = "SHA-256";
    public static final String SOURCE_CHECKSUM_SCOPE = "PER_FILE";
    public static final String CONTINUITY_SCOPE = "EXPLICIT_RESEARCH_STRATA";
    public static final String SESSION_TRANSITION_STATUS = "IMPLEMENTED_RESEARCH_ONLY";
    public static final String NORMALIZATION_VERSION = "histdata-m1-normalization-v1";
    private static final Set<String> SUPPORTED_CLOSURE_TYPES = Set.of(
            "SCHEDULED", "SCHEDULED_RECURRING", "HOLIDAY", "EXCEPTIONAL");
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final Duration UNAPPROVED = Duration.ZERO;
    public static final String CONTINUITY_POLICY_VERSION = Ml01ExploratorySessionContinuityEvaluator.POLICY_VERSION;

    private Ml01HistDataPreparation() {
    }

    public static PreparationResult prepare(Path inputDirectory, Path outputDirectory,
                                              PartitionConfiguration partitions) throws IOException {
        return prepare(inputDirectory, outputDirectory, partitions, null, List.of());
    }

    public static PreparationResult prepare(Path inputDirectory, Path outputDirectory,
                                              PartitionConfiguration partitions,
                                              List<ClosureEvidence> closureEvidence) throws IOException {
        return prepare(inputDirectory, outputDirectory, partitions, null, closureEvidence);
    }

    public static PreparationResult prepare(Path inputDirectory, Path outputDirectory,
                                             PartitionConfiguration partitions,
                                             PreparationScope scope,
                                             List<ClosureEvidence> closureEvidence) throws IOException {
        Objects.requireNonNull(inputDirectory, "input directory");
        Objects.requireNonNull(outputDirectory, "output directory");
        Objects.requireNonNull(partitions, "partition configuration");
        if (Files.exists(outputDirectory)) {
            try (var files = Files.list(outputDirectory)) {
                if (files.findAny().isPresent()) {
                    throw new IllegalArgumentException("output directory must be empty: " + outputDirectory);
                }
            }
        }
        Files.createDirectories(outputDirectory);

        List<HistDataOfflinePipeline.ImportResult> imports = List.of(
                HistDataOfflinePipeline.read(inputDirectory.resolve("DAT_ASCII_EURUSD_M1_202609.csv"),
                        marketId("EUR/USD"), "EUR/USD", HistDataOfflinePipeline.SOURCE_OFFSET),
                HistDataOfflinePipeline.read(inputDirectory.resolve("DAT_ASCII_GBPUSD_M1_202609.csv"),
                        marketId("GBP/USD"), "GBP/USD", HistDataOfflinePipeline.SOURCE_OFFSET)).stream()
                .filter(value -> scope == null || scope.marketIds().contains(value.marketId()))
                .toList();
        if (imports.isEmpty()) throw new IllegalArgumentException("ML-01 scope selects no supported market");

        List<Row> rows = new ArrayList<>();
        Map<String, Object> quality = new LinkedHashMap<>();
        Ml01H1AdmissibilityEvaluator admissibilityEvaluator = new Ml01H1AdmissibilityEvaluator();
        for (var imported : imports) {
            List<HistDataOfflinePipeline.AggregatedCandle> candles =
                    HistDataOfflinePipeline.aggregate(imported, HistDataOfflinePipeline.Timeframe.H1,
                            HistDataOfflinePipeline.SOURCE_OFFSET);
            List<HistDataOfflinePipeline.AggregatedCandle> admissible = candles.stream()
                    .filter(value -> admissibilityEvaluator
                            .evaluate(HistDataOfflinePipeline.qualityEvidence(imported, value)).eligible()).toList();
            quality.put(imported.symbol(), quality(imported, candles, admissible, closureEvidence));
            rows.addAll(rows(imported, candles, closureEvidence));
        }
        if (scope != null) {
            rows = new ArrayList<>(rows.stream().filter(value -> !value.cutoff().isBefore(scope.startCutoffInclusive())
                    && value.cutoff().isBefore(scope.endCutoffExclusive())).toList());
        }
        rows.sort(Comparator.comparing(Row::instrument).thenComparing(Row::cutoff));
        rows = assignPartitions(rows, partitions);

        Map<String, String> sourceChecksums = imports.stream().collect(Collectors.toMap(
                value -> value.symbol(), value -> sourceChecksum(value.sourceFile()),
                (left, right) -> left, LinkedHashMap::new));
        String fingerprint = fingerprint(rows, partitions, closureEvidence, sourceChecksums);
        writeRows(outputDirectory.resolve("observations.jsonl"), rows);
        writeJson(outputDirectory.resolve("dataset-manifest.json"), manifest(rows, imports, fingerprint, partitions,
                sourceChecksums, scope, closureEvidence));
        writeJson(outputDirectory.resolve("quality-report.json"), qualityReport(rows, imports, quality));
        writeJson(outputDirectory.resolve("partition-manifest.json"), partitionReport(rows, partitions));
        writeJson(outputDirectory.resolve("readiness.json"), readiness(rows, imports, partitions));
        writeJson(outputDirectory.resolve("source-metadata.json"), sourceMetadata(imports, fingerprint, sourceChecksums));
        return new PreparationResult(outputDirectory, fingerprint, rows.size(),
                (int) rows.stream().filter(Row::eligible).count(), readinessStatus(rows, imports, partitions));
    }

    public static void validatePublishedBundle(Path outputDirectory, Path sourceDirectory) throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var manifest = mapper.readTree(Files.readString(outputDirectory.resolve("dataset-manifest.json")));
        require(manifest.path("timeframe").asText(), "H1");
        require(manifest.path("researchFeatureSchemaVersion").asText(), RESEARCH_FEATURE_SCHEMA_VERSION);
        require(manifest.path("researchFeatureEvidencePolicy").asText(), RESEARCH_EXPORT_POLICY);
        require(manifest.path("admissibilityProfile").asText(), Ml01ExploratoryH1AdmissibilityProfile.V1.name());
        require(manifest.path("admissibilityPolicyVersion").asText(), Ml01ExploratoryH1AdmissibilityProfile.V1.version());
        require(manifest.path("outcomeDefinitionVersion").asText(), OUTCOME_DEFINITION_VERSION);
        require(manifest.path("continuityScope").asText(), CONTINUITY_SCOPE);
        require(manifest.path("sessionTransitionStatus").asText(), SESSION_TRANSITION_STATUS);
        require(manifest.path("sourceChecksumAlgorithm").asText(), SOURCE_CHECKSUM_ALGORITHM);
        require(manifest.path("sourceChecksumScope").asText(), SOURCE_CHECKSUM_SCOPE);
        require(manifest.path("normalizationVersion").asText(), NORMALIZATION_VERSION);
        require(manifest.path("continuityPolicyId").asText(), Ml01ExploratorySessionContinuityEvaluator.POLICY_ID);
        require(manifest.path("continuityPolicyVersion").asText(), CONTINUITY_POLICY_VERSION);
        require(manifest.path("continuityPolicyParameters").path("sourceTimezone").asText(),
                Ml01ExploratorySessionContinuityEvaluator.SOURCE_OFFSET.toString());
        require(manifest.path("continuityPolicyParameters").path("nominalClosure").asText(),
                "FRIDAY_17_TO_SUNDAY_17_SOURCE_LOCAL");
        require(manifest.path("continuityPolicyParameters").path("latestPermittedReopening").asText(),
                "SUNDAY_18_SOURCE_LOCAL");
        require(manifest.path("continuityPolicyParameters").path("maximumGap").asText(),
                Ml01ExploratorySessionContinuityEvaluator.MAXIMUM_GAP.toString());
        if (manifest.path("continuityPolicyParameters").path("maximumTransitions").asInt(-1)
                != Ml01ExploratorySessionContinuityEvaluator.MAXIMUM_TRANSITIONS) {
            throw new IllegalStateException("ML-01 continuity transition limit mismatch");
        }
        if (manifest.path("startCutoffInclusive").isMissingNode()
                || manifest.path("endCutoffExclusive").isMissingNode()) {
            throw new IllegalStateException("ML-01 manifest cutoff contract is incomplete");
        }
        var metadata = mapper.readTree(Files.readString(outputDirectory.resolve("source-metadata.json")));
        if (!manifest.path("sourceChecksums").equals(metadata.path("sourceChecksums"))) {
            throw new IllegalStateException("ML-01 source checksum metadata mismatch");
        }
        for (var entry : List.of(
                Map.entry("EUR/USD", "DAT_ASCII_EURUSD_M1_202609.csv"),
                Map.entry("GBP/USD", "DAT_ASCII_GBPUSD_M1_202609.csv"))) {
            if (!manifest.path("sourceChecksums").has(entry.getKey())) continue;
            String actual = sourceChecksum(sourceDirectory.resolve(entry.getValue()));
            if (!actual.equals(manifest.path("sourceChecksums").path(entry.getKey()).asText())) {
                throw new IllegalStateException("ML-01 source checksum mismatch: " + entry.getKey());
            }
        }
        List<String> rowFingerprints = new ArrayList<>();
        for (String line : Files.readAllLines(outputDirectory.resolve("observations.jsonl"))) {
            if (line.isBlank()) continue;
            var row = mapper.readTree(line);
            require(row.path("researchFeatureSchemaVersion").asText(), RESEARCH_FEATURE_SCHEMA_VERSION);
            require(row.path("continuityScope").asText(), CONTINUITY_SCOPE);
            require(row.path("sessionTransitionStatus").asText(), SESSION_TRANSITION_STATUS);
            if (row.path("admissibilityDecision").isMissingNode()
                    || row.path("rowFingerprint").asText().isBlank()) {
                throw new IllegalStateException("ML-01 row provenance is incomplete");
            }
            String recordedRowFingerprint = row.path("rowFingerprint").asText();
            ObjectNode rowMaterial = (ObjectNode) row.deepCopy();
            rowMaterial.remove("rowFingerprint");
            String actualRowFingerprint = ArtifactFingerprint.ofInputs(List.of(rowMaterial.toString())).value();
            if (!recordedRowFingerprint.equals(actualRowFingerprint)) {
                throw new IllegalStateException("ML-01 row fingerprint mismatch: " + recordedRowFingerprint
                        + " != " + actualRowFingerprint);
            }
            rowFingerprints.add(recordedRowFingerprint);
        }
        if (rowFingerprints.size() != manifest.path("rowCount").asLong(-1)
                || manifest.path("datasetFingerprint").asText().isBlank()) {
            throw new IllegalStateException("ML-01 bundle row or fingerprint contract is invalid");
        }
        List<String> material = new ArrayList<>(List.of(DATASET_DEFINITION_VERSION, SOURCE_PROFILE,
                RESEARCH_FEATURE_SCHEMA_VERSION, OUTCOME_DEFINITION_VERSION, FEATURES.toString(), HORIZONS.toString(),
                 CONTINUITY_POLICY_VERSION, Ml01ExploratorySessionContinuityEvaluator.POLICY_ID,
                 Ml01ExploratorySessionContinuityEvaluator.SOURCE_OFFSET.toString(),
                 "FRIDAY_17_TO_SUNDAY_17_SOURCE_LOCAL", "SUNDAY_18_SOURCE_LOCAL",
                 Ml01ExploratorySessionContinuityEvaluator.MAXIMUM_GAP.toString(),
                Integer.toString(Ml01ExploratorySessionContinuityEvaluator.MAXIMUM_TRANSITIONS),
                Ml01ExploratoryH1AdmissibilityProfile.V1.name(),
                Ml01ExploratoryH1AdmissibilityProfile.V1.version(),
                Integer.toString(Ml01ExploratoryH1AdmissibilityProfile.V1.minimumValidM1Count()),
                Boolean.toString(Ml01ExploratoryH1AdmissibilityProfile.V1.firstMinuteRequired()),
                Boolean.toString(Ml01ExploratoryH1AdmissibilityProfile.V1.lastMinuteRequired()),
                RESEARCH_EXPORT_POLICY, CONTINUITY_SCOPE, SESSION_TRANSITION_STATUS,
                SOURCE_CHECKSUM_ALGORITHM, SOURCE_CHECKSUM_SCOPE, NORMALIZATION_VERSION));
        manifest.path("sourceChecksums").fields().forEachRemaining(entry ->
                material.add("sourceChecksum=" + entry.getKey() + "|" + entry.getValue().asText()));
        manifest.path("closureEvidenceFingerprints").forEach(value -> material.add("closure=" + value.asText()));
        material.add("partition=" + text(manifest, "trainEndExclusive") + "|"
                + text(manifest, "validationEndExclusive") + "|" + text(manifest, "testEndExclusive") + "|"
                + (manifest.path("embargo").isNull() ? "PT0S" : manifest.path("embargo").asText()));
        rowFingerprints.forEach(value -> material.add("row=" + value));
        if (!manifest.path("datasetFingerprint").asText().equals(ArtifactFingerprint.ofInputs(material).value())) {
            throw new IllegalStateException("ML-01 dataset fingerprint mismatch");
        }
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
        return node.path(field).isNull() ? "null" : node.path(field).asText();
    }

    private static void require(String actual, String expected) {
        if (!expected.equals(actual)) throw new IllegalStateException("ML-01 contract mismatch: " + expected);
    }

    public record PartitionConfiguration(Instant trainEndExclusive, Instant validationEndExclusive,
                                         Instant testEndExclusive, Duration embargo) {
        public PartitionConfiguration {
            boolean complete = trainEndExclusive != null && validationEndExclusive != null
                    && testEndExclusive != null && embargo != null;
            if (complete && (!trainEndExclusive.isBefore(validationEndExclusive)
                    || !validationEndExclusive.isBefore(testEndExclusive) || embargo.isNegative())) {
                throw new IllegalArgumentException("partition boundaries must be chronological and embargo non-negative");
            }
            if (!complete && (trainEndExclusive != null || validationEndExclusive != null
                    || testEndExclusive != null || embargo != null)) {
                throw new IllegalArgumentException("all partition parameters are required together");
            }
        }

        public static PartitionConfiguration requiresApproval() {
            return new PartitionConfiguration(null, null, null, null);
        }

        public boolean isComplete() {
            return trainEndExclusive != null && validationEndExclusive != null
                    && testEndExclusive != null && embargo != null;
        }

        public Duration effectiveEmbargo() {
            return embargo == null ? UNAPPROVED : embargo;
        }
    }

    public record PreparationResult(Path outputDirectory, String datasetFingerprint, int rows,
                                    int eligibleRows, String readinessStatus) {
    }

    public record PreparationScope(Set<UUID> marketIds, Instant startCutoffInclusive,
                                   Instant endCutoffExclusive) {
        public PreparationScope {
            marketIds = Set.copyOf(marketIds);
            if (marketIds.isEmpty() || startCutoffInclusive == null || endCutoffExclusive == null
                    || !startCutoffInclusive.isBefore(endCutoffExclusive)) {
                throw new IllegalArgumentException("valid ML-01 preparation scope is required");
            }
        }
    }

    public record ClosureEvidence(UUID evidenceId, UUID marketId, String instrument, String interval,
                                  String venue, String closureType, String reason,
                                  Instant closureStart, Instant closureEnd,
                                  Instant knownAt, Instant effectiveFrom, Instant effectiveTo,
                                  String sourceId, String sourceVersion, String fingerprint) {
        public ClosureEvidence {
            if (evidenceId == null || instrument == null || instrument.isBlank()
                    || interval == null || interval.isBlank()
                    || venue == null || venue.isBlank() || closureStart == null || closureEnd == null
                    || closureType == null || closureType.isBlank() || reason == null || reason.isBlank()
                    || knownAt == null || effectiveFrom == null || effectiveTo == null
                    || sourceId == null || sourceId.isBlank()
                    || sourceVersion == null || sourceVersion.isBlank()
                    || fingerprint == null || fingerprint.isBlank()) {
                throw new IllegalArgumentException("complete closure evidence is required");
            }
            if (!closureStart.isBefore(closureEnd) || !effectiveFrom.isBefore(effectiveTo)
                    || closureStart.isBefore(effectiveFrom) || closureEnd.isAfter(effectiveTo)) {
                throw new IllegalArgumentException("closureStart must be before closureEnd");
            }
        }

        public ClosureEvidence(String instrument, Instant closureStart, Instant closureEnd,
                               Instant knownAt, String sourceId, String sourceVersion,
                               String fingerprint) {
            this(UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8)), null, instrument, "H1",
                    "HISTDATA", "HOLIDAY", "legacy synthetic evidence", closureStart, closureEnd, knownAt,
                    Instant.MIN, Instant.MAX, sourceId, sourceVersion, fingerprint);
        }
    }

    public enum TransitionClassification {
        CONTIGUOUS,
        VERIFIED_CLOSURE,
        UNEXPLAINED_GAP,
        UNKNOWN_CLOSURE_EVIDENCE,
        INVALID_OBSERVATION
    }

    private record Transition(TransitionClassification classification, ClosureEvidence evidence,
                              String reason) {
    }

    private record Row(String instrument, UUID marketId, Instant cutoff,
                       ResearchFeatureProjection snapshot,
                       Ml01H1AdmissibilityDecision admissibilityDecision,
                       Map<Integer, FutureOutcome> outcomes, boolean eligible, String exclusionReason,
                       int featureTransitionCount, String continuityStratum,
                       Duration featureElapsedDuration, List<TransitionProvenance> featureTransitionProvenance,
                       String partition, String partitionExclusion) {
    }

    private record FutureOutcome(int horizon, String status, Instant futureStart, Instant futureEnd,
                                 BigDecimal realizedVolatility, BigDecimal directionalEfficiency,
                                 BigDecimal absoluteFutureNetReturn, List<String> transitionClassifications,
                                 List<TransitionProvenance> transitionProvenance,
                                   int featureTransitionCount, int futureTransitionCount,
                                   int combinedTransitionCount, String continuityStratum,
                                   Duration actualElapsedDuration, String reason, String fingerprint) {
    }

    private record TransitionProvenance(String classification, int previousIndex, int nextIndex,
                                        LocalDateTime nominalClosureStart, LocalDateTime nominalClosureEnd,
                                        LocalDateTime latestPermittedReopening, Duration actualElapsed,
                                        String reason) {
    }

    private record ResearchFeatureProjection(String schemaVersion, String fingerprint,
                                             Map<String, ResearchFeatureValue> values,
                                             Map<String, Object> provenance) {
    }

    private record ResearchFeatureValue(String status, BigDecimal value) {
    }

    private record ContinuitySeries(List<Ml01ExploratorySessionContinuityEvaluator.Observation> observations,
                                    List<Integer> candidateIndexes,
                                    List<Ml01ExploratorySessionContinuityEvaluator.Transition> transitions) {
    }

    private static List<Row> rows(HistDataOfflinePipeline.ImportResult imported,
                                  List<HistDataOfflinePipeline.AggregatedCandle> candles,
                                  List<ClosureEvidence> closureEvidence) {
        List<Row> rows = new ArrayList<>();
        ContinuitySeries series = continuitySeries(imported, candles, closureEvidence);
        for (int candidatePosition = 0; candidatePosition < series.candidateIndexes().size(); candidatePosition++) {
            int index = series.candidateIndexes().get(candidatePosition);
            var reference = candles.get(index);
            var admissibilityDecision = series.observations().get(index).admissibility();
            List<HistDataOfflinePipeline.AggregatedCandle> history = acceptedHistory(series, candidatePosition);
            UUID executionId = stableId("ml01:execution:" + imported.symbol() + ":" + reference.openTime());
            String contextFingerprint = ArtifactFingerprint.ofInputs(List.of(
                    SOURCE_PROFILE, imported.symbol(), HistDataOfflinePipeline.SOURCE_SEMANTICS,
                    HistDataOfflinePipeline.PRICE_SIDE, reference.closeTime().toString(),
                    historyFingerprint(history))).value();
            ResearchFeatureProjection snapshot = directAnalysis(imported, reference, history, executionId,
                    contextFingerprint, closureEvidence, admissibilityDecision.state(),
                    history.stream().anyMatch(value -> series.observations().stream()
                            .anyMatch(observation -> observation.candle().equals(value)
                                    && observation.admissibility().state() == Ml01H1ResearchQualityState.PARTIAL_EXPLORATORY)));
            Map<Integer, FutureOutcome> outcomes = new LinkedHashMap<>();
            for (int horizon : HORIZONS) {
                outcomes.put(horizon, outcome(series, candidatePosition, reference, horizon));
            }
            String exclusion = featureAvailable(snapshot) ? null : "FEATURE_NOT_PRESENT";
            if (exclusion == null) {
                exclusion = outcomes.values().stream()
                        .filter(value -> !"AVAILABLE".equals(value.status()))
                        .map(FutureOutcome::reason)
                        .findFirst()
                        .orElse(null);
            }
            int featureTransitions = featureTransitionCount(series, candidatePosition);
            String stratum = stratum(featureTransitions);
            Duration featureElapsed = Duration.between(history.getFirst().openTime(), reference.closeTime());
            List<TransitionProvenance> featureTransitionProvenance = featureTransitionProvenance(series, candidatePosition);
            rows.add(new Row(imported.symbol(), imported.marketId(), reference.closeTime(), snapshot,
                    admissibilityDecision, outcomes,
                    exclusion == null, exclusion, featureTransitions, stratum, featureElapsed,
                    featureTransitionProvenance, null, null));
        }
        return rows;
    }

    private static ContinuitySeries continuitySeries(HistDataOfflinePipeline.ImportResult imported,
                                                     List<HistDataOfflinePipeline.AggregatedCandle> candles,
                                                     List<ClosureEvidence> closureEvidence) {
        Ml01H1AdmissibilityEvaluator evaluator = new Ml01H1AdmissibilityEvaluator();
        List<Ml01ExploratorySessionContinuityEvaluator.Observation> observations = candles.stream()
                .map(candle -> new Ml01ExploratorySessionContinuityEvaluator.Observation(candle,
                        evaluator.evaluate(HistDataOfflinePipeline.qualityEvidence(imported, candle))))
                .toList();
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < observations.size(); index++) {
            if (observations.get(index).eligible()) candidates.add(index);
        }
        List<Ml01ExploratorySessionContinuityEvaluator.Transition> transitions = new ArrayList<>();
        Ml01ExploratorySessionContinuityEvaluator continuity = new Ml01ExploratorySessionContinuityEvaluator();
        for (int index = 1; index < candidates.size(); index++) {
            int previous = candidates.get(index - 1);
            int next = candidates.get(index);
            var evaluated = continuity.evaluate(observations, previous, next);
            var strict = transition(candles.get(previous), candles.get(next), imported.marketId(), imported.symbol(),
                    candles.get(previous).closeTime(), closureEvidence);
            if (strict.classification() == TransitionClassification.VERIFIED_CLOSURE) {
                evaluated = new Ml01ExploratorySessionContinuityEvaluator.Transition(
                        Ml01ExploratorySessionContinuityEvaluator.Classification.VERIFIED_CLOSURE,
                        previous, next, null, null, null,
                        Duration.between(candles.get(previous).closeTime(), candles.get(next).openTime()),
                        strict.reason());
            }
            transitions.add(evaluated);
        }
        return new ContinuitySeries(observations, List.copyOf(candidates), List.copyOf(transitions));
    }

    private static ResearchFeatureProjection directAnalysis(HistDataOfflinePipeline.ImportResult imported,
                                                            HistDataOfflinePipeline.AggregatedCandle reference,
                                                            List<HistDataOfflinePipeline.AggregatedCandle> history,
                                                            UUID executionId, String contextFingerprint,
                                                            List<ClosureEvidence> closureEvidence,
                                                            Ml01H1ResearchQualityState qualityState,
                                                            boolean historyContainsPartialExploratory) {
        List<OhlcPoint> points = history.stream().map(value -> new OhlcPoint(value.openTime(), value.closeTime(),
                value.open(), value.high(), value.low(), value.close(), null)).toList();
        HistoricalOhlcContext context = new HistoricalOhlcContext(imported.marketId(), "H1", points);
        String inputFingerprint = ArtifactFingerprint.ofInputs(List.of(
                imported.sourceFile().getFileName().toString(), imported.symbol(), historyFingerprint(history))).value();
            VolatilityAssessment volatility = new VolatilityCalculator().calculate(
                    context, reference.closeTime(), inputFingerprint, history.size() > 1
                            && hasVerifiedClosure(history, imported.marketId(), imported.symbol(), closureEvidence));
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        if (volatility.status() == VolatilityAssessment.Status.AVAILABLE) {
            values.put("atr", volatility.atr());
            values.put("normalizedAtr", volatility.normalizedAtr());
            values.put("realizedVolatility", volatility.realizedVolatility());
            values.put("volatilityCompressionRatio", volatility.compressionRatio());
            values.put("directionalEfficiency", volatility.directionalEfficiency());
        }
        Map<String, ResearchFeatureValue> featureValues = new LinkedHashMap<>();
        featureValues.put("ohlc.normalized_atr", featureValue(volatility, values.get("normalizedAtr")));
        featureValues.put("ohlc.realized_volatility", featureValue(volatility, values.get("realizedVolatility")));
        featureValues.put("ohlc.volatility_compression_ratio", featureValue(volatility, values.get("volatilityCompressionRatio")));
        featureValues.put("ohlc.directional_efficiency", featureValue(volatility, values.get("directionalEfficiency")));
        String fingerprint = ArtifactFingerprint.ofInputs(List.of(RESEARCH_FEATURE_SCHEMA_VERSION,
                contextFingerprint, inputFingerprint, volatility.status().name(), String.valueOf(values))).value();
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("executionId", executionId);
        provenance.put("contextFingerprint", contextFingerprint);
        provenance.put("inputFingerprint", inputFingerprint);
        provenance.put("evidencePolicy", qualityState == Ml01H1ResearchQualityState.FULL_OBSERVED
                && !historyContainsPartialExploratory
                ? EvidenceAcquisitionPolicy.STRICT_OBSERVED.name() : "RESEARCH_EXPLORATORY");
        provenance.put("observationCount", points.size());
        provenance.put("cutoff", reference.closeTime());
        provenance.put("status", volatility.status());
        provenance.put("reason", volatility.reason());
        return new ResearchFeatureProjection(RESEARCH_FEATURE_SCHEMA_VERSION, fingerprint, featureValues, provenance);
    }

    private static ResearchFeatureValue featureValue(VolatilityAssessment assessment, BigDecimal value) {
        return new ResearchFeatureValue(assessment.status().name(), value);
    }

    private static FutureOutcome outcome(ContinuitySeries series, int candidatePosition,
                                         HistDataOfflinePipeline.AggregatedCandle reference, int horizon) {
        int endPosition = candidatePosition + horizon;
        int featureTransitions = featureTransitionCount(series, candidatePosition);
        if (endPosition >= series.candidateIndexes().size()) {
            return unavailable(horizon, featureTransitions, 0, featureTransitions,
                    stratum(featureTransitions), Duration.ZERO, List.of(), List.of(),
                    "INCOMPLETE_FORWARD_WINDOW", "future window is unavailable");
        }
        List<HistDataOfflinePipeline.AggregatedCandle> future = new ArrayList<>();
        for (int position = candidatePosition + 1; position <= endPosition; position++) {
            future.add(series.observations().get(series.candidateIndexes().get(position)).candle());
        }
        List<String> classifications = new ArrayList<>();
        List<TransitionProvenance> provenance = new ArrayList<>();
        int futureTransitions = 0;
        String rejection = null;
        for (int position = candidatePosition; position < endPosition; position++) {
            var transition = series.transitions().get(position);
            classifications.add(transition.classification().name());
            provenance.add(transitionProvenance(transition));
            if (transition.assumed()) futureTransitions++;
            if (transition.classification() != Ml01ExploratorySessionContinuityEvaluator.Classification.CONTIGUOUS
                    && transition.classification() != Ml01ExploratorySessionContinuityEvaluator.Classification.VERIFIED_CLOSURE
                    && transition.classification() != Ml01ExploratorySessionContinuityEvaluator.Classification.ASSUMED_SESSION_TRANSITION
                    && rejection == null) rejection = transition.classification().name() + ":" + transition.reason();
        }
        int combinedTransitions = featureTransitions + futureTransitions;
        Duration elapsed = Duration.between(reference.closeTime(), future.getLast().closeTime());
        String stratum = stratum(combinedTransitions);
        if (rejection != null || combinedTransitions > Ml01ExploratorySessionContinuityEvaluator.MAXIMUM_TRANSITIONS) {
            return unavailable(horizon, featureTransitions, futureTransitions, combinedTransitions, stratum, elapsed,
                    classifications, provenance,
                    rejection == null ? "MULTIPLE_ASSUMED_SESSION_TRANSITIONS" : rejection, "continuity window rejected");
        }
        List<BigDecimal> closes = new ArrayList<>();
        closes.add(reference.close());
        closes.addAll(future.stream().map(HistDataOfflinePipeline.AggregatedCandle::close).toList());
        BigDecimal sumSquares = BigDecimal.ZERO;
        BigDecimal path = BigDecimal.ZERO;
        for (int i = 1; i < closes.size(); i++) {
            BigDecimal change = closes.get(i).subtract(closes.get(i - 1), MC)
                    .divide(closes.get(i - 1), MC);
            sumSquares = sumSquares.add(change.multiply(change, MC), MC);
            path = path.add(closes.get(i).subtract(closes.get(i - 1), MC).abs(), MC);
        }
        BigDecimal efficiency = path.signum() == 0 ? BigDecimal.ZERO
                : closes.getLast().subtract(closes.getFirst(), MC).abs().divide(path, MC);
        BigDecimal absoluteReturn = closes.getLast().subtract(closes.getFirst(), MC)
                .divide(closes.getFirst(), MC).abs();
        Instant start = future.getFirst().openTime();
        Instant end = future.getLast().closeTime();
        String fingerprint = ArtifactFingerprint.ofInputs(List.of(OUTCOME_DEFINITION_VERSION,
                reference.symbol(), reference.closeTime().toString(), Integer.toString(horizon),
                start.toString(), end.toString(), closes.stream().map(BigDecimal::toPlainString).toList().toString())).value();
        return new FutureOutcome(horizon, "AVAILABLE", start, end,
                 sumSquares.divide(BigDecimal.valueOf(horizon), MC).sqrt(MC), efficiency, absoluteReturn,
                 classifications, provenance, featureTransitions, futureTransitions, combinedTransitions, stratum, elapsed,
                 null, fingerprint);
    }

    private static FutureOutcome unavailable(int horizon, int featureTransitions, int futureTransitions,
                                             int combinedTransitions, String stratum, Duration elapsed,
                                             List<String> classifications, List<TransitionProvenance> provenance,
                                             String reason, String detail) {
        return new FutureOutcome(horizon, "UNAVAILABLE", null, null, null, null, null,
                classifications, provenance, featureTransitions, futureTransitions, combinedTransitions, stratum, elapsed,
                reason + ":" + detail, null);
    }

    private static int featureTransitionCount(ContinuitySeries series, int candidatePosition) {
        int start = featureStart(series, candidatePosition);
        int count = 0;
        for (int position = start; position < candidatePosition; position++) {
            if (series.transitions().get(position).assumed()) count++;
        }
        return count;
    }

    private static int featureStart(ContinuitySeries series, int candidatePosition) {
        int start = candidatePosition;
        while (start > 0) {
            var transition = series.transitions().get(start - 1);
            if (transition.classification() != Ml01ExploratorySessionContinuityEvaluator.Classification.CONTIGUOUS
                    && transition.classification() != Ml01ExploratorySessionContinuityEvaluator.Classification.ASSUMED_SESSION_TRANSITION) break;
            start--;
            if (candidatePosition - start >= 32) break;
        }
        return start;
    }

    private static List<TransitionProvenance> featureTransitionProvenance(ContinuitySeries series,
                                                                           int candidatePosition) {
        int start = featureStart(series, candidatePosition);
        return series.transitions().subList(start, candidatePosition).stream()
                .map(Ml01HistDataPreparation::transitionProvenance).toList();
    }

    private static TransitionProvenance transitionProvenance(
            Ml01ExploratorySessionContinuityEvaluator.Transition transition) {
        return new TransitionProvenance(transition.classification().name(), transition.previousIndex(),
                transition.nextIndex(), transition.nominalClosureStart(), transition.nominalClosureEnd(),
                transition.latestPermittedReopening(), transition.actualElapsed(), transition.reason());
    }

    private static String stratum(int transitionCount) {
        return transitionCount == 0 ? "CONTIGUOUS_ONLY" : "ASSUMED_SESSION_TRANSITION";
    }

    private static boolean featureAvailable(ResearchFeatureProjection snapshot) {
        return FEATURES.stream().allMatch(name -> snapshot.values().get(name) != null
                && snapshot.values().get(name).value() != null
                && "AVAILABLE".equals(snapshot.values().get(name).status()));
    }

    private static List<HistDataOfflinePipeline.AggregatedCandle> acceptedHistory(ContinuitySeries series,
                                                                                    int candidatePosition) {
        int start = candidatePosition;
        while (candidatePosition - start < 32 && start > 0) {
            var transition = series.transitions().get(start - 1);
            if (transition.classification() != Ml01ExploratorySessionContinuityEvaluator.Classification.CONTIGUOUS
                    && transition.classification() != Ml01ExploratorySessionContinuityEvaluator.Classification.ASSUMED_SESSION_TRANSITION) {
                break;
            }
            start--;
        }
        List<HistDataOfflinePipeline.AggregatedCandle> history = new ArrayList<>();
        for (int position = start; position <= candidatePosition; position++) {
            history.add(series.observations().get(series.candidateIndexes().get(position)).candle());
        }
        return history;
    }

    private static List<HistDataOfflinePipeline.AggregatedCandle> acceptedHistory(
            List<HistDataOfflinePipeline.AggregatedCandle> candles, UUID marketId, String instrument, Instant cutoff,
            List<ClosureEvidence> closureEvidence) {
        if (candles.isEmpty()) return List.of();
        int start = candles.size() - 1;
        for (int index = candles.size() - 2; index >= 0; index--) {
            Transition transition = transition(candles.get(index), candles.get(index + 1), marketId, instrument, cutoff, closureEvidence);
            if (transition.classification() == TransitionClassification.UNEXPLAINED_GAP
                    || transition.classification() == TransitionClassification.UNKNOWN_CLOSURE_EVIDENCE
                    || transition.classification() == TransitionClassification.INVALID_OBSERVATION
                    || transition.classification() == TransitionClassification.VERIFIED_CLOSURE) break;
            start = index;
        }
        return candles.subList(start, candles.size());
    }

    private static boolean hasVerifiedClosure(List<HistDataOfflinePipeline.AggregatedCandle> candles,
                                               UUID marketId, String instrument, List<ClosureEvidence> closureEvidence) {
        for (int index = 1; index < candles.size(); index++) {
            if (transition(candles.get(index - 1), candles.get(index), marketId, instrument,
                    candles.getLast().closeTime(), closureEvidence).classification()
                    == TransitionClassification.VERIFIED_CLOSURE) return true;
        }
        return false;
    }

    private static Transition transition(HistDataOfflinePipeline.AggregatedCandle previous,
                                         HistDataOfflinePipeline.AggregatedCandle next,
                                         UUID marketId, String instrument, Instant cutoff,
                                         List<ClosureEvidence> closureEvidence) {
        if (previous.state() != HistDataOfflinePipeline.AggregationState.COMPLETE
                || next.state() != HistDataOfflinePipeline.AggregationState.COMPLETE) {
            return new Transition(TransitionClassification.INVALID_OBSERVATION, null,
                    "one or both H1 observations are not complete");
        }
        if (previous.closeTime().isAfter(next.openTime())) {
            return new Transition(TransitionClassification.INVALID_OBSERVATION, null,
                    "H1 observations are not chronologically ordered");
        }
        if (previous.closeTime().equals(next.openTime())) {
            return new Transition(TransitionClassification.CONTIGUOUS, null, "H1 observations are adjacent");
        }
        List<ClosureEvidence> applicable = (closureEvidence == null ? List.<ClosureEvidence>of() : closureEvidence)
                .stream()
                .filter(value -> (value.marketId() == null || value.marketId().equals(marketId))
                        && value.instrument().equals(instrument)
                        && "H1".equals(value.interval())
                        && value.closureStart().compareTo(previous.closeTime()) <= 0
                        && value.closureEnd().compareTo(next.openTime()) >= 0
                        && !value.effectiveFrom().isAfter(previous.closeTime())
                        && !value.effectiveTo().isBefore(next.openTime())
                        && !value.knownAt().isAfter(cutoff))
                .toList();
        if (applicable.size() == 1 && SUPPORTED_CLOSURE_TYPES.contains(applicable.getFirst().closureType().toUpperCase())) {
            return new Transition(TransitionClassification.VERIFIED_CLOSURE, applicable.getFirst(),
                    "scoped closure evidence covers the transition");
        }
        return new Transition(applicable.isEmpty() ? TransitionClassification.UNEXPLAINED_GAP
                : TransitionClassification.UNKNOWN_CLOSURE_EVIDENCE, null,
                applicable.isEmpty() ? "no applicable closure evidence" : "closure evidence is ambiguous");
    }

    static TransitionClassification classifyTransition(HistDataOfflinePipeline.AggregatedCandle previous,
                                                       HistDataOfflinePipeline.AggregatedCandle next,
                                                       Instant cutoff, List<ClosureEvidence> closureEvidence) {
        return transition(previous, next, previous.marketId(), previous.symbol(), cutoff, closureEvidence).classification();
    }

    private static Map<String, Object> quality(HistDataOfflinePipeline.ImportResult imported,
                                               List<HistDataOfflinePipeline.AggregatedCandle> all,
                                               List<HistDataOfflinePipeline.AggregatedCandle> complete,
                                               List<ClosureEvidence> closureEvidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceRows", imported.observations().size() + imported.invalidRows() + imported.malformedRows()
                + imported.exactDuplicateRows() + imported.conflictingDuplicateRows());
        result.put("malformedRows", imported.malformedRows());
        result.put("invalidRows", imported.invalidRows());
        result.put("exactDuplicateRowsDeduplicated", imported.exactDuplicateRows());
        result.put("conflictingDuplicateRowsQuarantined", imported.conflictingDuplicateRows());
        result.put("chronologicalInversions", imported.chronologicalInversions());
        result.put("missingM1Slots", imported.missingM1Slots());
        result.put("h1CandleCounts", all.stream().collect(Collectors.groupingBy(
                value -> value.state().name(), LinkedHashMap::new, Collectors.counting())));
        result.put("admissibleH1Candles", complete.size());
        result.put("verifiedClosureEvidence", closureEvidence != null && !closureEvidence.isEmpty());
        result.put("continuityPolicyVersion", CONTINUITY_POLICY_VERSION);
        result.put("continuityPolicyId", Ml01ExploratorySessionContinuityEvaluator.POLICY_ID);
        result.put("continuityPolicyParameters", Map.of(
                "provider", HistDataOfflinePipeline.SOURCE_PROVIDER,
                "sourceTimezone", Ml01ExploratorySessionContinuityEvaluator.SOURCE_OFFSET.toString(),
                "nominalClosure", "FRIDAY_17_TO_SUNDAY_17_SOURCE_LOCAL",
                "latestPermittedReopening", "SUNDAY_18_SOURCE_LOCAL",
                "maximumGap", Ml01ExploratorySessionContinuityEvaluator.MAXIMUM_GAP.toString(),
                "maximumTransitions", Ml01ExploratorySessionContinuityEvaluator.MAXIMUM_TRANSITIONS,
                "usage", "RETROSPECTIVE_EXPLORATORY_ONLY"));
        result.put("closureEvidenceFingerprints", closureEvidence == null ? List.of()
                : closureEvidence.stream().map(ClosureEvidence::fingerprint).sorted().toList());
        result.put("closureEvidenceSources", closureEvidence == null ? List.of()
                : closureEvidence.stream().map(value -> value.sourceId() + "@" + value.sourceVersion()
                        + "|" + value.venue()).sorted().toList());
        Map<String, Long> transitionCounts = new LinkedHashMap<>();
        for (int index = 1; index < complete.size(); index++) {
            String classification = transition(complete.get(index - 1), complete.get(index), imported.marketId(), imported.symbol(),
                    complete.get(index).closeTime(), closureEvidence).classification().name();
            transitionCounts.merge(classification, 1L, Long::sum);
        }
        result.put("continuityTransitionCounts", transitionCounts);
        result.put("unexplainedGapsFailClosed", true);
        return result;
    }

    private static Map<String, Object> manifest(List<Row> rows,
                                                List<HistDataOfflinePipeline.ImportResult> imports,
                                                 String fingerprint, PartitionConfiguration partitions,
                                                 Map<String, String> sourceChecksums,
                                                 PreparationScope scope,
                                                 List<ClosureEvidence> closureEvidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetDefinitionVersion", DATASET_DEFINITION_VERSION);
        result.put("datasetProfile", DATASET_DEFINITION_VERSION);
        result.put("sourceProfile", SOURCE_PROFILE);
        result.put("provider", HistDataOfflinePipeline.SOURCE_PROVIDER);
        result.put("timeframe", "H1");
        result.put("markets", imports.stream().map(HistDataOfflinePipeline.ImportResult::symbol).toList());
        result.put("marketIds", imports.stream().map(value -> value.marketId().toString()).toList());
        result.put("startCutoffInclusive", scope == null ? rows.stream().map(Row::cutoff).min(Instant::compareTo).orElse(null)
                : scope.startCutoffInclusive());
        result.put("endCutoffExclusive", scope == null ? rows.stream().map(Row::cutoff).max(Instant::compareTo)
                .map(value -> value.plusSeconds(3600)).orElse(null) : scope.endCutoffExclusive());
        result.put("researchFeatureSchemaVersion", RESEARCH_FEATURE_SCHEMA_VERSION);
        result.put("researchFeatureEvidencePolicy", RESEARCH_EXPORT_POLICY);
        result.put("admissibilityProfile", Ml01ExploratoryH1AdmissibilityProfile.V1.name());
        result.put("admissibilityPolicyVersion", Ml01ExploratoryH1AdmissibilityProfile.V1.version());
        result.put("continuityScope", CONTINUITY_SCOPE);
        result.put("sessionTransitionStatus", SESSION_TRANSITION_STATUS);
        result.put("sourceChecksumAlgorithm", SOURCE_CHECKSUM_ALGORITHM);
        result.put("sourceChecksumScope", SOURCE_CHECKSUM_SCOPE);
        result.put("sourceChecksums", sourceChecksums);
        result.put("normalizationVersion", NORMALIZATION_VERSION);
        result.put("continuityPolicyId", Ml01ExploratorySessionContinuityEvaluator.POLICY_ID);
        result.put("continuityPolicyVersion", CONTINUITY_POLICY_VERSION);
        result.put("continuityPolicyParameters", continuityPolicyParameters());
        result.put("continuityStrata", rows.stream().map(Row::continuityStratum).distinct().sorted().toList());
        result.put("closureEvidenceFingerprints", closureEvidence == null ? List.of()
                : closureEvidence.stream().map(ClosureEvidence::fingerprint).sorted().toList());
        result.put("features", FEATURES);
        result.put("outcomeDefinitionVersion", OUTCOME_DEFINITION_VERSION);
        result.put("horizons", HORIZONS.stream().map(value -> Map.of("validH1Candles", value)).toList());
        result.put("rowCount", rows.size());
        result.put("eligibleCount", rows.stream().filter(Row::eligible).count());
        result.put("excludedCount", rows.stream().filter(value -> !value.eligible()).count());
        result.put("exclusionCounts", rows.stream().filter(value -> !value.eligible())
                .collect(Collectors.groupingBy(Row::exclusionReason, LinkedHashMap::new, Collectors.counting())));
        result.put("partitionParametersProvided", partitions.isComplete());
        result.put("trainEndExclusive", partitions.trainEndExclusive());
        result.put("validationEndExclusive", partitions.validationEndExclusive());
        result.put("testEndExclusive", partitions.testEndExclusive());
        result.put("embargo", partitions.isComplete() ? partitions.embargo().toString() : null);
        result.put("datasetFingerprint", fingerprint);
        result.put("fingerprintMaterialVersion", "ml01-h1-fingerprint-v1");
        result.put("generatedAt", Instant.now().toString());
        return result;
    }

    private static Map<String, Object> qualityReport(List<Row> rows,
                                                     List<HistDataOfflinePipeline.ImportResult> imports,
                                                     Map<String, Object> quality) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rowsByInstrument", rows.stream().collect(Collectors.groupingBy(Row::instrument, LinkedHashMap::new, Collectors.counting())));
        result.put("eligibleByInstrument", rows.stream().filter(Row::eligible).collect(Collectors.groupingBy(Row::instrument, LinkedHashMap::new, Collectors.counting())));
        result.put("qualityStateCounts", rows.stream().collect(Collectors.groupingBy(
                value -> value.admissibilityDecision().state().name(), LinkedHashMap::new, Collectors.counting())));
        result.put("partialRowsAreResearchOnly", true);
        result.put("outcomeAvailabilityByHorizon", HORIZONS.stream().collect(Collectors.toMap(
                horizon -> Integer.toString(horizon), horizon -> rows.stream().filter(value ->
                        "AVAILABLE".equals(value.outcomes().get(horizon).status())).count(),
                (left, right) -> left, LinkedHashMap::new)));
        result.put("sourceQuality", quality);
        result.put("futureWindowsOverlapWithinPartitionPermitted", true);
        result.put("futureWindowOverlapReportedAsDependence", true);
        result.put("crossBoundaryOverlapPurged", true);
        result.put("closurePolicy", CONTINUITY_POLICY_VERSION);
        result.put("imports", imports.stream().map(value -> value.sourceFile().getFileName().toString()).toList());
        return result;
    }

    private static Map<String, Object> partitionReport(List<Row> rows, PartitionConfiguration configuration) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", configuration.isComplete() ? "CONFIGURED" : "REQUIRES_SCIENTIFIC_APPROVAL");
        result.put("trainEndExclusive", value(configuration.trainEndExclusive()));
        result.put("validationEndExclusive", value(configuration.validationEndExclusive()));
        result.put("testEndExclusive", value(configuration.testEndExclusive()));
        result.put("embargo", configuration.isComplete() ? configuration.embargo().toString() : null);
        result.put("commonBoundaries", true);
        result.put("rowsAssigned", configuration.isComplete()
                ? rows.stream().filter(value -> value.partition() != null).count() : 0);
        result.put("effectiveSamplesByPartition", rows.stream()
                .filter(value -> value.eligible() && value.partitionExclusion() == null && value.partition() != null)
                .collect(Collectors.groupingBy(Row::partition, LinkedHashMap::new, Collectors.counting())));
        result.put("partitionExclusionCounts", rows.stream().filter(value -> value.partitionExclusion() != null)
                .collect(Collectors.groupingBy(Row::partitionExclusion, LinkedHashMap::new, Collectors.counting())));
        result.put("purgePolicy", "FUTURE_WINDOW_MUST_NOT_CROSS_PROTECTED_BOUNDARY");
        result.put("ordinaryWithinPartitionOverlap", "PERMITTED_AND_REPORTED_AS_STATISTICAL_DEPENDENCE");
        result.put("minimumEffectiveSamples", "UNAPPROVED");
        return result;
    }

    private static Map<String, Object> readiness(List<Row> rows, List<HistDataOfflinePipeline.ImportResult> imports,
                                                 PartitionConfiguration partitions) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> blockers = new ArrayList<>();
        if (imports.stream().anyMatch(value -> value.conflictingDuplicateRows() > 0)) {
            blockers.add("CONFLICTING_DUPLICATE_OR_QUARANTINED_SOURCE_ROWS");
        }
        if (rows.stream().noneMatch(Row::eligible)) blockers.add("NO_ELIGIBLE_ML01_ROWS");
        if (!partitions.isComplete()) blockers.add("SCIENTIFIC_PARTITION_PARAMETERS_UNAPPROVED");
        result.put("status", readinessStatus(rows, imports, partitions));
        result.put("pipelineValidated", !rows.isEmpty() && imports.stream().allMatch(value -> value.conflictingDuplicateRows() == 0));
        result.put("requiresScientificApproval", !partitions.isComplete());
        result.put("kmeansExecuted", false);
        result.put("modelTrainingExecuted", false);
        result.put("forwardAssociationEvaluated", false);
        result.put("blockers", blockers);
        result.put("eligibleRows", rows.stream().filter(Row::eligible).count());
        return result;
    }

    private static String readinessStatus(List<Row> rows, List<HistDataOfflinePipeline.ImportResult> imports,
                                          PartitionConfiguration partitions) {
        if (rows.isEmpty() || imports.stream().anyMatch(value -> value.conflictingDuplicateRows() > 0)) {
            return "BLOCKED_BY_DATA_INTEGRITY";
        }
        if (rows.stream().noneMatch(Row::eligible)) return "INSUFFICIENT_DATA";
        return partitions.isComplete() ? "PIPELINE_VALIDATED" : "REQUIRES_SCIENTIFIC_APPROVAL";
    }

    private static Map<String, Object> sourceMetadata(List<HistDataOfflinePipeline.ImportResult> imports,
                                                      String fingerprint,
                                                      Map<String, String> sourceChecksums) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", HistDataOfflinePipeline.SOURCE_PROVIDER);
        result.put("sourceProfile", SOURCE_PROFILE);
        result.put("sourceSemantics", HistDataOfflinePipeline.SOURCE_SEMANTICS);
        result.put("sourceTimezone", "EST / UTC-05:00, fixed offset, no daylight saving adjustment");
        result.put("sourceFiles", imports.stream().map(value -> value.sourceFile().getFileName().toString()).toList());
        result.put("sourceFingerprints", imports.stream().collect(Collectors.toMap(
                HistDataOfflinePipeline.ImportResult::symbol, Ml01HistDataPreparation::sourceFingerprint,
                (left, right) -> left, LinkedHashMap::new)));
        result.put("sourceChecksumAlgorithm", SOURCE_CHECKSUM_ALGORITHM);
        result.put("sourceChecksumScope", SOURCE_CHECKSUM_SCOPE);
        result.put("sourceChecksums", sourceChecksums);
        result.put("normalizationVersion", NORMALIZATION_VERSION);
        result.put("datasetFingerprint", fingerprint);
        return result;
    }

    private static String fingerprint(List<Row> rows, PartitionConfiguration partitions,
                                      List<ClosureEvidence> closureEvidence,
                                      Map<String, String> sourceChecksums) {
        List<String> material = new ArrayList<>(List.of(DATASET_DEFINITION_VERSION, SOURCE_PROFILE,
                RESEARCH_FEATURE_SCHEMA_VERSION, OUTCOME_DEFINITION_VERSION, FEATURES.toString(), HORIZONS.toString(),
                 CONTINUITY_POLICY_VERSION, Ml01ExploratorySessionContinuityEvaluator.POLICY_ID,
                 Ml01ExploratorySessionContinuityEvaluator.SOURCE_OFFSET.toString(),
                 "FRIDAY_17_TO_SUNDAY_17_SOURCE_LOCAL", "SUNDAY_18_SOURCE_LOCAL",
                 Ml01ExploratorySessionContinuityEvaluator.MAXIMUM_GAP.toString(),
                Integer.toString(Ml01ExploratorySessionContinuityEvaluator.MAXIMUM_TRANSITIONS),
                Ml01ExploratoryH1AdmissibilityProfile.V1.name(),
                Ml01ExploratoryH1AdmissibilityProfile.V1.version(),
                Integer.toString(Ml01ExploratoryH1AdmissibilityProfile.V1.minimumValidM1Count()),
                Boolean.toString(Ml01ExploratoryH1AdmissibilityProfile.V1.firstMinuteRequired()),
                Boolean.toString(Ml01ExploratoryH1AdmissibilityProfile.V1.lastMinuteRequired()),
                RESEARCH_EXPORT_POLICY, CONTINUITY_SCOPE, SESSION_TRANSITION_STATUS,
                SOURCE_CHECKSUM_ALGORITHM, SOURCE_CHECKSUM_SCOPE, NORMALIZATION_VERSION));
        sourceChecksums.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> material.add("sourceChecksum=" + entry.getKey() + "|" + entry.getValue()));
        if (closureEvidence != null) {
            closureEvidence.stream().map(ClosureEvidence::fingerprint).sorted()
                    .forEach(value -> material.add("closure=" + value));
        }
        material.add("partition=" + partitions.trainEndExclusive() + "|" + partitions.validationEndExclusive()
                + "|" + partitions.testEndExclusive() + "|" + partitions.effectiveEmbargo());
        rows.forEach(row -> {
            material.add("row=" + rowFingerprint(row));
        });
        return ArtifactFingerprint.ofInputs(material).value();
    }

    private static Map<String, Object> continuityPolicyParameters() {
        return Map.of(
                "provider", HistDataOfflinePipeline.SOURCE_PROVIDER,
                "sourceTimezone", Ml01ExploratorySessionContinuityEvaluator.SOURCE_OFFSET.toString(),
                "nominalClosure", "FRIDAY_17_TO_SUNDAY_17_SOURCE_LOCAL",
                "latestPermittedReopening", "SUNDAY_18_SOURCE_LOCAL",
                "maximumGap", Ml01ExploratorySessionContinuityEvaluator.MAXIMUM_GAP.toString(),
                "maximumTransitions", Ml01ExploratorySessionContinuityEvaluator.MAXIMUM_TRANSITIONS,
                "usage", "RETROSPECTIVE_EXPLORATORY_ONLY");
    }

    private static void writeRows(Path path, List<Row> rows) throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Files.write(path, rows.stream().map(row -> {
            try {
                return mapper.writeValueAsString(rowDocument(row));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }).toList(), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> rowDocument(Row row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(rowMaterial(row));
        result.put("rowFingerprint", rowFingerprint(row));
        return result;
    }

    private static Map<String, Object> rowMaterial(Row row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instrument", row.instrument());
        result.put("marketId", row.marketId());
        result.put("evaluationCutoff", row.cutoff());
        result.put("timeframe", "H1");
        result.put("researchFeatureSchemaVersion", row.snapshot().schemaVersion());
        result.put("researchFeatureFingerprint", row.snapshot().fingerprint());
        result.put("featureProvenance", row.snapshot().provenance());
        result.put("admissibilityDecision", row.admissibilityDecision());
        result.put("continuityScope", CONTINUITY_SCOPE);
        result.put("sessionTransitionStatus", SESSION_TRANSITION_STATUS);
        result.put("continuityPolicyId", Ml01ExploratorySessionContinuityEvaluator.POLICY_ID);
        result.put("continuityPolicyVersion", CONTINUITY_POLICY_VERSION);
        result.put("featureTransitionCount", row.featureTransitionCount());
        result.put("continuityStratum", row.continuityStratum());
        result.put("featureElapsedDuration", row.featureElapsedDuration());
        result.put("featureTransitionProvenance", row.featureTransitionProvenance());
        Map<String, Object> features = new LinkedHashMap<>();
        for (String name : FEATURES) {
            ResearchFeatureValue numeric = row.snapshot().values().get(name);
            Map<String, Object> feature = new LinkedHashMap<>();
            feature.put("status", numeric.status());
            feature.put("value", numeric.value());
            features.put(name, feature);
        }
        result.put("features", features);
        result.put("outcomes", row.outcomes());
        result.put("eligible", row.eligible());
        result.put("exclusionReason", row.exclusionReason());
        result.put("partition", row.partition());
        result.put("partitionExclusion", row.partitionExclusion());
        return result;
    }

    private static String rowFingerprint(Row row) {
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            String serialized = mapper.writeValueAsString(rowMaterial(row));
            return ArtifactFingerprint.ofInputs(List.of(mapper.readTree(serialized).toString())).value();
        } catch (IOException exception) {
            throw new IllegalStateException("unable to fingerprint ML-01 row", exception);
        }
    }

    private static List<Row> assignPartitions(List<Row> rows, PartitionConfiguration configuration) {
        if (!configuration.isComplete()) return rows;
        return rows.stream().map(row -> {
            String partition;
            if (row.cutoff().isBefore(configuration.trainEndExclusive())) partition = "TRAIN";
            else if (row.cutoff().isBefore(configuration.validationEndExclusive())) partition = "VALIDATION";
            else if (row.cutoff().isBefore(configuration.testEndExclusive())) partition = "TEST";
            else return new Row(row.instrument(), row.marketId(), row.cutoff(), row.snapshot(),
                    row.admissibilityDecision(), row.outcomes(),
                    row.eligible(), row.exclusionReason(), row.featureTransitionCount(), row.continuityStratum(),
                    row.featureElapsedDuration(), row.featureTransitionProvenance(),
                    "OUT_OF_RANGE", "OUTSIDE_PARTITION_BOUNDS");

            String exclusion = null;
            Instant protectedBoundary = "TRAIN".equals(partition) ? configuration.trainEndExclusive()
                    : "VALIDATION".equals(partition) ? configuration.validationEndExclusive() : null;
            if (protectedBoundary != null && latestFutureEnd(row).isAfter(protectedBoundary)) {
                exclusion = "FORWARD_WINDOW_CROSSES_PARTITION_BOUNDARY";
            }
            if (exclusion == null && "VALIDATION".equals(partition)
                    && row.cutoff().isBefore(configuration.trainEndExclusive().plus(configuration.embargo()))) {
                exclusion = "EMBARGO_AFTER_TRAIN";
            }
            if (exclusion == null && "TEST".equals(partition)
                    && row.cutoff().isBefore(configuration.validationEndExclusive().plus(configuration.embargo()))) {
                exclusion = "EMBARGO_AFTER_VALIDATION";
            }
            return new Row(row.instrument(), row.marketId(), row.cutoff(), row.snapshot(),
                    row.admissibilityDecision(), row.outcomes(),
                    row.eligible(), row.exclusionReason(), row.featureTransitionCount(), row.continuityStratum(),
                    row.featureElapsedDuration(), row.featureTransitionProvenance(), partition, exclusion);
        }).toList();
    }

    private static Instant latestFutureEnd(Row row) {
        return row.outcomes().values().stream().map(FutureOutcome::futureEnd)
                .filter(Objects::nonNull).max(Instant::compareTo).orElse(Instant.MIN);
    }

    private static void writeJson(Path path, Object value) throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n",
                StandardCharsets.UTF_8);
    }

    private static String value(Instant value) {
        return value == null ? null : value.toString();
    }

    private static String historyFingerprint(List<HistDataOfflinePipeline.AggregatedCandle> candles) {
        return ArtifactFingerprint.ofInputs(candles.stream().map(value -> value.sourceOpenTime() + "|"
                + value.open() + "|" + value.high() + "|" + value.low() + "|" + value.close()).toList()).value();
    }

    private static String sourceFingerprint(HistDataOfflinePipeline.ImportResult imported) {
        return HistDataOfflinePipeline.sourceFingerprint(imported);
    }

    private static String sourceChecksum(Path sourceFile) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance(SOURCE_CHECKSUM_ALGORITHM)
                            .digest(Files.readAllBytes(sourceFile)));
        } catch (IOException | java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("unable to checksum source file: " + sourceFile, exception);
        }
    }

    private static UUID marketId(String symbol) {
        return UUID.nameUUIDFromBytes(("histdata:market:" + symbol).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        Path input = args.length > 0 ? Path.of(args[0]) : Path.of("histData");
        Path output = args.length > 1 ? Path.of(args[1]) : input.resolve("ml01-h1");
        PreparationResult result = prepare(input, output, PartitionConfiguration.requiresApproval());
        System.out.printf("rows=%d eligible=%d readiness=%s fingerprint=%s output=%s%n",
                result.rows(), result.eligibleRows(), result.readinessStatus(), result.datasetFingerprint(),
                result.outputDirectory());
    }
}
