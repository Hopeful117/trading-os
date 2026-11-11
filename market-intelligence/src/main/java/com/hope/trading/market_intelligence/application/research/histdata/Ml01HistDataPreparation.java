package com.hope.trading.market_intelligence.application.research.histdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.feature.FeatureProjectionV2;
import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.HistoricalOhlcContext;
import com.hope.trading.market_intelligence.domain.OhlcPoint;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactIdentity;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactProvenance;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactScope;
import com.hope.trading.market_intelligence.domain.artifact.DeterministicMeasurements;
import com.hope.trading.market_intelligence.domain.artifact.StoredArtifact;
import com.hope.trading.market_intelligence.domain.capability.ProducedArtifact;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.feature.FeatureValue;
import com.hope.trading.market_intelligence.domain.feature.NumericFeatureValue;
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
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final Duration UNAPPROVED = Duration.ZERO;
    private static final String CONTINUITY_POLICY_VERSION = "ml01-h1-continuity-v1";

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
        for (var imported : imports) {
            List<HistDataOfflinePipeline.AggregatedCandle> candles =
                    HistDataOfflinePipeline.aggregate(imported, HistDataOfflinePipeline.Timeframe.H1,
                            HistDataOfflinePipeline.SOURCE_OFFSET);
            List<HistDataOfflinePipeline.AggregatedCandle> complete = candles.stream()
                    .filter(value -> value.state() == HistDataOfflinePipeline.AggregationState.COMPLETE)
                    .toList();
            quality.put(imported.symbol(), quality(imported, candles, complete, closureEvidence));
            rows.addAll(rows(imported, complete, closureEvidence));
        }
        if (scope != null) {
            rows = new ArrayList<>(rows.stream().filter(value -> !value.cutoff().isBefore(scope.startCutoffInclusive())
                    && value.cutoff().isBefore(scope.endCutoffExclusive())).toList());
        }
        rows.sort(Comparator.comparing(Row::instrument).thenComparing(Row::cutoff));
        rows = assignPartitions(rows, partitions);

        String fingerprint = fingerprint(rows, partitions, closureEvidence);
        writeRows(outputDirectory.resolve("observations.jsonl"), rows);
        writeJson(outputDirectory.resolve("dataset-manifest.json"), manifest(rows, imports, fingerprint, partitions));
        writeJson(outputDirectory.resolve("quality-report.json"), qualityReport(rows, imports, quality));
        writeJson(outputDirectory.resolve("partition-manifest.json"), partitionReport(rows, partitions));
        writeJson(outputDirectory.resolve("readiness.json"), readiness(rows, imports, partitions));
        writeJson(outputDirectory.resolve("source-metadata.json"), sourceMetadata(imports, fingerprint));
        return new PreparationResult(outputDirectory, fingerprint, rows.size(),
                (int) rows.stream().filter(Row::eligible).count(), readinessStatus(rows, imports, partitions));
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

    public record ClosureEvidence(String instrument, Instant closureStart, Instant closureEnd,
                                  Instant knownAt, Instant effectiveFrom, Instant effectiveTo,
                                  String venue, String sourceId, String sourceVersion,
                                  String fingerprint) {
        public ClosureEvidence {
            if (instrument == null || instrument.isBlank() || closureStart == null || closureEnd == null
                    || knownAt == null || effectiveFrom == null || effectiveTo == null
                    || venue == null || venue.isBlank() || sourceId == null || sourceId.isBlank()
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
            this(instrument, closureStart, closureEnd, knownAt, Instant.MIN, Instant.MAX,
                    "HISTDATA", sourceId, sourceVersion, fingerprint);
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

    private record Row(String instrument, UUID marketId, Instant cutoff, FeatureSnapshot snapshot,
                       Map<Integer, FutureOutcome> outcomes, boolean eligible, String exclusionReason,
                       String partition, String partitionExclusion) {
    }

    private record FutureOutcome(int horizon, String status, Instant futureStart, Instant futureEnd,
                                 BigDecimal realizedVolatility, BigDecimal directionalEfficiency,
                                 BigDecimal absoluteFutureNetReturn, List<String> transitionClassifications,
                                 String reason, String fingerprint) {
    }

    private static List<Row> rows(HistDataOfflinePipeline.ImportResult imported,
                                  List<HistDataOfflinePipeline.AggregatedCandle> candles,
                                  List<ClosureEvidence> closureEvidence) {
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < candles.size(); index++) {
            var reference = candles.get(index);
            List<HistDataOfflinePipeline.AggregatedCandle> history = acceptedHistory(
                    candles.subList(0, index + 1), imported.symbol(), reference.closeTime(), closureEvidence);
            UUID executionId = stableId("ml01:execution:" + imported.symbol() + ":" + reference.openTime());
            String contextFingerprint = ArtifactFingerprint.ofInputs(List.of(
                    SOURCE_PROFILE, imported.symbol(), HistDataOfflinePipeline.SOURCE_SEMANTICS,
                    HistDataOfflinePipeline.PRICE_SIDE, reference.closeTime().toString(),
                    historyFingerprint(history))).value();
            ProducedArtifact analysis = directAnalysis(imported, reference, history, executionId, contextFingerprint,
                    closureEvidence);
            FeatureSnapshot snapshot = snapshot(imported, reference, analysis, executionId, contextFingerprint);
            Map<Integer, FutureOutcome> outcomes = new LinkedHashMap<>();
            for (int horizon : HORIZONS) {
                outcomes.put(horizon, outcome(imported, candles, index, reference, horizon, closureEvidence));
            }
            String exclusion = featureAvailable(snapshot) ? null : "FEATURE_NOT_PRESENT";
            if (exclusion == null) {
                exclusion = outcomes.values().stream().filter(value -> !"AVAILABLE".equals(value.status()))
                        .map(FutureOutcome::reason).findFirst().orElse(null);
            }
            rows.add(new Row(imported.symbol(), imported.marketId(), reference.closeTime(), snapshot, outcomes,
                    exclusion == null, exclusion, null, null));
        }
        return rows;
    }

    private static FeatureSnapshot snapshot(HistDataOfflinePipeline.ImportResult imported,
                                            HistDataOfflinePipeline.AggregatedCandle reference,
                                            ProducedArtifact analysis, UUID executionId,
                                            String contextFingerprint) {
        if (analysis == null) {
            throw new IllegalStateException("H1 analysis did not produce an artifact");
        }
        var projection = com.hope.trading.market_intelligence.application.feature.FeatureProjectionV2.project(
                executionId, imported.marketId(), reference.closeTime(), EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                contextFingerprint, List.of(analysis), List.of(), List.of(), Instant.parse("2026-10-02T00:00:00Z"));
        return new FeatureSnapshot(stableId("ml01:snapshot:" + imported.symbol() + ":" + reference.openTime()),
                executionId, imported.marketId(), reference.closeTime(), EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                FeatureProjectionV2.VERSION, contextFingerprint, projection.values(), projection.groups(),
                projection.provenance(), projection.fingerprint(), Instant.parse("2026-10-02T00:00:00Z"));
    }

    private static ProducedArtifact directAnalysis(HistDataOfflinePipeline.ImportResult imported,
                                                   HistDataOfflinePipeline.AggregatedCandle reference,
                                                   List<HistDataOfflinePipeline.AggregatedCandle> history,
                                                   UUID executionId, String contextFingerprint,
                                                   List<ClosureEvidence> closureEvidence) {
        List<OhlcPoint> points = history.stream().map(value -> new OhlcPoint(value.openTime(), value.closeTime(),
                value.open(), value.high(), value.low(), value.close(), null)).toList();
        HistoricalOhlcContext context = new HistoricalOhlcContext(imported.marketId(), "H1", points);
        String inputFingerprint = ArtifactFingerprint.ofInputs(List.of(
                imported.sourceFile().getFileName().toString(), imported.symbol(), historyFingerprint(history))).value();
            VolatilityAssessment volatility = new VolatilityCalculator().calculate(
                    context, reference.closeTime(), inputFingerprint, history.size() > 1
                            && hasVerifiedClosure(history, imported.symbol(), closureEvidence));
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        if (volatility.status() == VolatilityAssessment.Status.AVAILABLE) {
            values.put("atr", volatility.atr());
            values.put("normalizedAtr", volatility.normalizedAtr());
            values.put("realizedVolatility", volatility.realizedVolatility());
            values.put("volatilityCompressionRatio", volatility.compressionRatio());
            values.put("directionalEfficiency", volatility.directionalEfficiency());
        }
        DeterministicMeasurements measurements = new DeterministicMeasurements(
                "ML-01 H1 volatility measurements", volatility.reason(), values, reference.closeTime());
        ArtifactIdentity identity = new ArtifactIdentity("ml01-h1-volatility", "ml01-histdata", OUTCOME_DEFINITION_VERSION);
        StoredArtifact artifact = new StoredArtifact(
                new com.hope.trading.market_intelligence.domain.artifact.ArtifactCacheKey(
                        identity, ArtifactScope.publicMarket(imported.marketId(), "H1",
                        com.hope.trading.market_intelligence.domain.AnalysisExecutionMode.PASSIVE),
                        ArtifactFingerprint.empty(), ArtifactFingerprint.ofInputs(List.of(inputFingerprint))),
                measurements,
                com.hope.trading.market_intelligence.domain.artifact.ArtifactFreshness.validUntil(
                        reference.closeTime(), reference.closeTime().plusSeconds(1), OUTCOME_DEFINITION_VERSION),
                new ArtifactProvenance("ml01-histdata", OUTCOME_DEFINITION_VERSION, executionId,
                        reference.closeTime(), java.util.Set.of(), java.util.Set.of(), contextFingerprint,
                        inputFingerprint).withEvidenceContext(EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                        points.size(), reference.closeTime()),
                AnalysisResultQuality.COMPLETE, ProductionArtifactTypes.V1);
        return new ProducedArtifact(ProductionArtifactTypes.OHLC_RANGE_ANALYSIS, ProductionArtifactTypes.V1, artifact);
    }

    private static FutureOutcome outcome(HistDataOfflinePipeline.ImportResult imported,
                                         List<HistDataOfflinePipeline.AggregatedCandle> candles, int index,
                                         HistDataOfflinePipeline.AggregatedCandle reference, int horizon,
                                         List<ClosureEvidence> closureEvidence) {
        if (index + horizon >= candles.size()) {
            return unavailable(horizon, "INCOMPLETE_FORWARD_WINDOW", "future window is unavailable");
        }
        List<HistDataOfflinePipeline.AggregatedCandle> future = candles.subList(index + 1, index + horizon + 1);
        var previous = reference;
        List<String> classifications = new ArrayList<>();
        for (var candle : future) {
            Transition transition = transition(previous, candle, imported.symbol(), reference.closeTime(), closureEvidence);
            classifications.add(transition.classification().name());
            if (transition.classification() == TransitionClassification.UNEXPLAINED_GAP
                    || transition.classification() == TransitionClassification.UNKNOWN_CLOSURE_EVIDENCE
                    || transition.classification() == TransitionClassification.INVALID_OBSERVATION) {
                return unavailable(horizon, transition.classification().name(), transition.reason());
            }
            previous = candle;
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
                imported.symbol(), reference.closeTime().toString(), Integer.toString(horizon),
                start.toString(), end.toString(), closes.stream().map(BigDecimal::toPlainString).toList().toString())).value();
        return new FutureOutcome(horizon, "AVAILABLE", start, end,
                 sumSquares.divide(BigDecimal.valueOf(horizon), MC).sqrt(MC), efficiency, absoluteReturn,
                 classifications, null, fingerprint);
    }

    private static FutureOutcome unavailable(int horizon, String reason, String detail) {
        return new FutureOutcome(horizon, "UNAVAILABLE", null, null, null, null, null,
                List.of(), reason + ":" + detail, null);
    }

    private static boolean featureAvailable(FeatureSnapshot snapshot) {
        Map<String, FeatureValue> values = snapshot.values().stream()
                .collect(Collectors.toMap(FeatureValue::name, value -> value));
        return FEATURES.stream().allMatch(name -> values.get(name) instanceof NumericFeatureValue numeric
                && numeric.status() == FeatureMissingness.PRESENT && numeric.value() != null);
    }

    private static List<HistDataOfflinePipeline.AggregatedCandle> acceptedHistory(
            List<HistDataOfflinePipeline.AggregatedCandle> candles, String instrument, Instant cutoff,
            List<ClosureEvidence> closureEvidence) {
        if (candles.isEmpty()) return List.of();
        int start = candles.size() - 1;
        for (int index = candles.size() - 2; index >= 0; index--) {
            Transition transition = transition(candles.get(index), candles.get(index + 1), instrument, cutoff, closureEvidence);
            if (transition.classification() == TransitionClassification.UNEXPLAINED_GAP
                    || transition.classification() == TransitionClassification.UNKNOWN_CLOSURE_EVIDENCE
                    || transition.classification() == TransitionClassification.INVALID_OBSERVATION) break;
            start = index;
        }
        return candles.subList(start, candles.size());
    }

    private static boolean hasVerifiedClosure(List<HistDataOfflinePipeline.AggregatedCandle> candles,
                                              String instrument, List<ClosureEvidence> closureEvidence) {
        for (int index = 1; index < candles.size(); index++) {
            if (transition(candles.get(index - 1), candles.get(index), instrument,
                    candles.getLast().closeTime(), closureEvidence).classification()
                    == TransitionClassification.VERIFIED_CLOSURE) return true;
        }
        return false;
    }

    private static Transition transition(HistDataOfflinePipeline.AggregatedCandle previous,
                                         HistDataOfflinePipeline.AggregatedCandle next,
                                         String instrument, Instant cutoff,
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
                .filter(value -> value.instrument().equals(instrument)
                        && value.closureStart().compareTo(previous.closeTime()) <= 0
                        && value.closureEnd().compareTo(next.openTime()) >= 0
                        && !value.effectiveFrom().isAfter(previous.closeTime())
                        && !value.effectiveTo().isBefore(next.openTime())
                        && !value.knownAt().isAfter(cutoff))
                .toList();
        if (applicable.size() == 1) {
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
        return transition(previous, next, previous.symbol(), cutoff, closureEvidence).classification();
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
        result.put("completeH1Candles", complete.size());
        result.put("verifiedClosureEvidence", closureEvidence != null && !closureEvidence.isEmpty());
        result.put("continuityPolicyVersion", CONTINUITY_POLICY_VERSION);
        result.put("closureEvidenceFingerprints", closureEvidence == null ? List.of()
                : closureEvidence.stream().map(ClosureEvidence::fingerprint).sorted().toList());
        Map<String, Long> transitionCounts = new LinkedHashMap<>();
        for (int index = 1; index < complete.size(); index++) {
            String classification = transition(complete.get(index - 1), complete.get(index), imported.symbol(),
                    complete.get(index).closeTime(), closureEvidence).classification().name();
            transitionCounts.merge(classification, 1L, Long::sum);
        }
        result.put("continuityTransitionCounts", transitionCounts);
        result.put("unexplainedGapsFailClosed", true);
        return result;
    }

    private static Map<String, Object> manifest(List<Row> rows,
                                                List<HistDataOfflinePipeline.ImportResult> imports,
                                                String fingerprint, PartitionConfiguration partitions) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetDefinitionVersion", DATASET_DEFINITION_VERSION);
        result.put("sourceProfile", SOURCE_PROFILE);
        result.put("provider", HistDataOfflinePipeline.SOURCE_PROVIDER);
        result.put("timeframe", "H1");
        result.put("markets", imports.stream().map(HistDataOfflinePipeline.ImportResult::symbol).toList());
        result.put("marketIds", imports.stream().map(value -> value.marketId().toString()).toList());
        result.put("featureProjectionVersion", FeatureProjectionV2.VERSION);
        result.put("features", FEATURES);
        result.put("outcomeDefinitionVersion", OUTCOME_DEFINITION_VERSION);
        result.put("horizons", HORIZONS.stream().map(value -> Map.of("validH1Candles", value)).toList());
        result.put("rowCount", rows.size());
        result.put("eligibleCount", rows.stream().filter(Row::eligible).count());
        result.put("excludedCount", rows.stream().filter(value -> !value.eligible()).count());
        result.put("exclusionCounts", rows.stream().filter(value -> !value.eligible())
                .collect(Collectors.groupingBy(Row::exclusionReason, LinkedHashMap::new, Collectors.counting())));
        result.put("partitionParametersProvided", partitions.isComplete());
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
                                                      String fingerprint) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", HistDataOfflinePipeline.SOURCE_PROVIDER);
        result.put("sourceProfile", SOURCE_PROFILE);
        result.put("sourceSemantics", HistDataOfflinePipeline.SOURCE_SEMANTICS);
        result.put("sourceTimezone", "EST / UTC-05:00, fixed offset, no daylight saving adjustment");
        result.put("sourceFiles", imports.stream().map(value -> value.sourceFile().getFileName().toString()).toList());
        result.put("datasetFingerprint", fingerprint);
        return result;
    }

    private static String fingerprint(List<Row> rows, PartitionConfiguration partitions,
                                      List<ClosureEvidence> closureEvidence) {
        List<String> material = new ArrayList<>(List.of(DATASET_DEFINITION_VERSION, SOURCE_PROFILE,
                FeatureProjectionV2.VERSION, OUTCOME_DEFINITION_VERSION, FEATURES.toString(), HORIZONS.toString(),
                CONTINUITY_POLICY_VERSION));
        if (closureEvidence != null) {
            closureEvidence.stream().map(ClosureEvidence::fingerprint).sorted()
                    .forEach(value -> material.add("closure=" + value));
        }
        material.add("partition=" + partitions.trainEndExclusive() + "|" + partitions.validationEndExclusive()
                + "|" + partitions.testEndExclusive() + "|" + partitions.effectiveEmbargo());
        rows.forEach(row -> {
            material.add(row.instrument() + "|" + row.cutoff() + "|" + row.snapshot().fingerprint());
            row.outcomes().values().forEach(outcome -> material.add(outcome.horizon() + "|" + outcome.status()
                    + "|" + outcome.fingerprint()));
        });
        return ArtifactFingerprint.ofInputs(material).value();
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
        result.put("instrument", row.instrument());
        result.put("marketId", row.marketId());
        result.put("evaluationCutoff", row.cutoff());
        result.put("timeframe", "H1");
        result.put("featureProjectionVersion", row.snapshot().featureProjectionVersion());
        result.put("featureSnapshotFingerprint", row.snapshot().fingerprint());
        result.put("featureProvenance", row.snapshot().provenance());
        Map<String, Object> features = new LinkedHashMap<>();
        for (String name : FEATURES) {
            FeatureValue value = row.snapshot().values().stream().filter(item -> item.name().equals(name)).findFirst().orElseThrow();
            NumericFeatureValue numeric = (NumericFeatureValue) value;
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
        result.put("rowFingerprint", ArtifactFingerprint.ofInputs(List.of(row.instrument(), row.cutoff().toString(),
                row.snapshot().fingerprint(), row.outcomes().toString(), String.valueOf(row.eligible()),
                String.valueOf(row.exclusionReason()))).value());
        return result;
    }

    private static List<Row> assignPartitions(List<Row> rows, PartitionConfiguration configuration) {
        if (!configuration.isComplete()) return rows;
        return rows.stream().map(row -> {
            String partition;
            if (row.cutoff().isBefore(configuration.trainEndExclusive())) partition = "TRAIN";
            else if (row.cutoff().isBefore(configuration.validationEndExclusive())) partition = "VALIDATION";
            else if (row.cutoff().isBefore(configuration.testEndExclusive())) partition = "TEST";
            else return new Row(row.instrument(), row.marketId(), row.cutoff(), row.snapshot(), row.outcomes(),
                    row.eligible(), row.exclusionReason(), "OUT_OF_RANGE", "OUTSIDE_PARTITION_BOUNDS");

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
            return new Row(row.instrument(), row.marketId(), row.cutoff(), row.snapshot(), row.outcomes(),
                    row.eligible(), row.exclusionReason(), partition, exclusion);
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
