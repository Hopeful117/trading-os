package com.hope.trading.market_intelligence.application.research.histdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.capability.OhlcRangeAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.application.feature.FeatureProjectionV1;
import com.hope.trading.market_intelligence.application.feature.FeatureProjectionV2;
import com.hope.trading.market_intelligence.application.research.DatasetBuildResult;
import com.hope.trading.market_intelligence.application.research.ResearchDatasetBuilder;
import com.hope.trading.market_intelligence.application.research.ResearchDatasetBundleExporter;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.HistoricalOhlcContext;
import com.hope.trading.market_intelligence.domain.OhlcPoint;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.outcome.*;
import com.hope.trading.market_intelligence.domain.research.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reproducible, database-free HistData import and V2 export.
 *
 * The source files use fixed EST without daylight-saving adjustments. The
 * parser therefore requires a fixed offset rather than a DST-aware zone.
 */
public final class HistDataOfflinePipeline {
    public static final String SOURCE_PROVIDER = "HISTDATA";
    public static final String SOURCE_SEMANTICS = "EST_FIXED_UTC_MINUS_05_NO_DST";
    public static final String PRICE_SIDE = "BID";
    public static final String NORMALIZATION_VERSION = "histdata-offline-v1";
    public static final ZoneOffset SOURCE_OFFSET = ZoneOffset.ofHours(-5);
    private static final DateTimeFormatter SOURCE_FORMAT = DateTimeFormatter.ofPattern("uuuuMMdd HHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Instant RUN_TIME = Instant.parse("2026-10-02T00:00:00Z");
    private static final Duration OUTCOME_HORIZON = Duration.ofHours(1);
    private static final int HISTORY_LIMIT = 240;

    private HistDataOfflinePipeline() {
    }

    public enum Timeframe {
        M15(15), H1(60), H4(240);

        private final int minutes;

        Timeframe(int minutes) {
            this.minutes = minutes;
        }
    }

    public enum AggregationState {
        COMPLETE, INCOMPLETE, INVALID, SESSION_UNCERTAIN
    }

    public record RejectedObservation(int lineNumber, String raw, String reason) {
    }

    public record M1Observation(
            String symbol,
            Path sourceFile,
            int lineNumber,
            LocalDateTime sourceTimestamp,
            Instant openTime,
            Instant closeTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close
    ) {
    }

    public record ImportResult(
            UUID marketId,
            String symbol,
            Path sourceFile,
            List<M1Observation> observations,
            List<RejectedObservation> qualityEvents,
            Set<LocalDateTime> quarantinedTimestamps,
            Set<LocalDateTime> invalidTimestamps,
            int malformedRows,
            int invalidRows,
            int exactDuplicateRows,
            int conflictingDuplicateRows,
            int chronologicalInversions,
            int missingM1Slots,
            int sessionUncertainM1Slots,
            int nonSessionM1Slots
    ) {
        public ImportResult {
            observations = List.copyOf(observations);
            qualityEvents = List.copyOf(qualityEvents);
            quarantinedTimestamps = Set.copyOf(quarantinedTimestamps);
            invalidTimestamps = Set.copyOf(invalidTimestamps);
        }
    }

    public record AggregatedCandle(
            UUID marketId,
            String symbol,
            Timeframe timeframe,
            LocalDateTime sourceOpenTime,
            Instant openTime,
            Instant closeTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume,
            AggregationState state,
            int expectedM1Count,
            int observedM1Count,
            String sourceFile
    ) {
    }

    public record PipelineResult(
            List<ImportResult> imports,
            Map<String, Integer> completeCandleCounts,
            int featureSnapshotCount,
            int eligibleFeatureRows,
            int excludedFeatureRows,
            String datasetFingerprint,
            Path outputDirectory
    ) {
    }

    public static ImportResult read(Path sourceFile, UUID marketId, String symbol, ZoneOffset sourceOffset)
            throws IOException {
        Objects.requireNonNull(sourceFile, "source file");
        Objects.requireNonNull(marketId, "market ID");
        requireSymbol(symbol);
        Objects.requireNonNull(sourceOffset, "source offset");

        List<M1Observation> parsed = new ArrayList<>();
        List<RejectedObservation> events = new ArrayList<>();
        Set<LocalDateTime> invalidTimestamps = new HashSet<>();
        int malformedRows = 0;
        int invalidRows = 0;
        int inversions = 0;
        LocalDateTime previous = null;
        try (BufferedReader reader = Files.newBufferedReader(sourceFile, StandardCharsets.US_ASCII)) {
            String raw;
            int lineNumber = 0;
            while ((raw = reader.readLine()) != null) {
                lineNumber++;
                String[] fields = raw.split(";", -1);
                if (fields.length != 6) {
                    malformedRows++;
                    events.add(new RejectedObservation(lineNumber, raw, "MALFORMED_FIELD_COUNT"));
                    continue;
                }
                LocalDateTime sourceTimestamp;
                BigDecimal open;
                BigDecimal high;
                BigDecimal low;
                BigDecimal close;
                try {
                    sourceTimestamp = LocalDateTime.parse(fields[0], SOURCE_FORMAT);
                    open = new BigDecimal(fields[1]);
                    high = new BigDecimal(fields[2]);
                    low = new BigDecimal(fields[3]);
                    close = new BigDecimal(fields[4]);
                    new BigDecimal(fields[5]);
                } catch (RuntimeException exception) {
                    malformedRows++;
                    events.add(new RejectedObservation(lineNumber, raw, "MALFORMED_TIMESTAMP_OR_NUMBER"));
                    continue;
                }
                if (sourceTimestamp.getSecond() != 0 || !validOhlc(open, high, low, close)) {
                    invalidRows++;
                    invalidTimestamps.add(sourceTimestamp);
                    events.add(new RejectedObservation(lineNumber, raw,
                            sourceTimestamp.getSecond() == 0 ? "INVALID_OHLC" : "M1_TIMESTAMP_NOT_ON_MINUTE"));
                    continue;
                }
                if (previous != null && sourceTimestamp.isBefore(previous)) inversions++;
                previous = sourceTimestamp;
                Instant openTime = sourceTimestamp.toInstant(sourceOffset);
                parsed.add(new M1Observation(symbol, sourceFile, lineNumber, sourceTimestamp,
                        openTime, openTime.plusSeconds(60), open, high, low, close));
            }
        }

        Map<LocalDateTime, List<M1Observation>> groups = parsed.stream()
                .collect(Collectors.groupingBy(M1Observation::sourceTimestamp, LinkedHashMap::new, Collectors.toList()));
        List<M1Observation> eligible = new ArrayList<>();
        Set<LocalDateTime> quarantined = new HashSet<>();
        int exactDuplicates = 0;
        int conflictingDuplicates = 0;
        for (Map.Entry<LocalDateTime, List<M1Observation>> entry : groups.entrySet()) {
            List<M1Observation> values = entry.getValue();
            if (values.size() == 1) {
                eligible.add(values.getFirst());
                continue;
            }
            M1Observation first = values.getFirst();
            boolean exact = values.stream().allMatch(value -> sameValues(first, value));
            if (exact) {
                exactDuplicates += values.size() - 1;
                eligible.add(first);
                values.stream().skip(1).forEach(value -> events.add(new RejectedObservation(value.lineNumber(),
                        "", "EXACT_DUPLICATE_DEDUPLICATED")));
            } else {
                conflictingDuplicates += values.size();
                quarantined.add(entry.getKey());
                values.forEach(value -> events.add(new RejectedObservation(value.lineNumber(),
                        "", "CONFLICTING_DUPLICATE_TIMESTAMP_QUARANTINED")));
            }
        }
        eligible.sort(Comparator.comparing(M1Observation::sourceTimestamp));
        int missing = 0;
        int uncertain = 0;
        int nonSession = 0;
        for (int index = 1; index < eligible.size(); index++) {
            LocalDateTime previousTimestamp = eligible.get(index - 1).sourceTimestamp();
            LocalDateTime currentTimestamp = eligible.get(index).sourceTimestamp();
            LocalDateTime cursor = previousTimestamp.plusMinutes(1);
            while (cursor.isBefore(currentTimestamp)) {
                missing++;
                if (weeklyClosureCandidate(cursor)) uncertain++;
                else nonSession++;
                cursor = cursor.plusMinutes(1);
            }
        }
        return new ImportResult(marketId, symbol, sourceFile, eligible, events, quarantined,
                invalidTimestamps, malformedRows, invalidRows, exactDuplicates, conflictingDuplicates,
                inversions, missing, uncertain, nonSession);
    }

    public static List<AggregatedCandle> aggregate(ImportResult input, Timeframe timeframe, ZoneOffset sourceOffset) {
        Objects.requireNonNull(input, "import result");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(sourceOffset, "source offset");
        if (input.observations().isEmpty()) return List.of();
        Map<LocalDateTime, M1Observation> observations = input.observations().stream()
                .collect(Collectors.toUnmodifiableMap(M1Observation::sourceTimestamp, value -> value));
        LocalDateTime firstBucket = bucketStart(input.observations().getFirst().sourceTimestamp(), timeframe.minutes);
        LocalDateTime lastBucket = bucketStart(input.observations().getLast().sourceTimestamp(), timeframe.minutes);
        List<AggregatedCandle> result = new ArrayList<>();
        for (LocalDateTime bucket = firstBucket; !bucket.isAfter(lastBucket); bucket = bucket.plusMinutes(timeframe.minutes)) {
            List<LocalDateTime> expected = new ArrayList<>();
            for (int minute = 0; minute < timeframe.minutes; minute++) expected.add(bucket.plusMinutes(minute));
            List<M1Observation> present = expected.stream().map(observations::get).filter(Objects::nonNull).toList();
            List<LocalDateTime> missing = expected.stream().filter(value -> !observations.containsKey(value)).toList();
            boolean invalid = expected.stream().anyMatch(input.invalidTimestamps()::contains);
            boolean conflict = expected.stream().anyMatch(input.quarantinedTimestamps()::contains);
            AggregationState state;
            if (invalid) state = AggregationState.INVALID;
            else if (conflict) state = AggregationState.INCOMPLETE;
            else if (missing.isEmpty()) state = AggregationState.COMPLETE;
            else if (missing.stream().allMatch(HistDataOfflinePipeline::weeklyClosureCandidate)) {
                state = AggregationState.SESSION_UNCERTAIN;
            } else state = AggregationState.INCOMPLETE;
            Instant openTime = bucket.toInstant(sourceOffset);
            BigDecimal open = present.isEmpty() ? null : present.getFirst().open();
            BigDecimal close = present.isEmpty() ? null : present.getLast().close();
            BigDecimal high = present.stream().map(M1Observation::high).max(BigDecimal::compareTo).orElse(null);
            BigDecimal low = present.stream().map(M1Observation::low).min(BigDecimal::compareTo).orElse(null);
            result.add(new AggregatedCandle(input.marketId(), input.symbol(), timeframe, bucket, openTime,
                    openTime.plusSeconds(timeframe.minutes * 60L), open, high, low, close, null, state,
                    expected.size(), present.size(), input.sourceFile().getFileName().toString()));
        }
        return List.copyOf(result);
    }

    public static PipelineResult run(Path inputDirectory, Path outputDirectory) throws IOException {
        Objects.requireNonNull(inputDirectory, "input directory");
        Objects.requireNonNull(outputDirectory, "output directory");
        if (Files.exists(outputDirectory)) throw new IllegalArgumentException("output directory already exists: " + outputDirectory);
        List<ImportResult> imports = List.of(
                read(inputDirectory.resolve("DAT_ASCII_EURUSD_M1_202609.csv"), marketId("EUR/USD"), "EUR/USD", SOURCE_OFFSET),
                read(inputDirectory.resolve("DAT_ASCII_GBPUSD_M1_202609.csv"), marketId("GBP/USD"), "GBP/USD", SOURCE_OFFSET));
        Map<String, List<AggregatedCandle>> candles = new LinkedHashMap<>();
        Map<String, Integer> completeCounts = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Integer>>> candleQuality = new LinkedHashMap<>();
        for (ImportResult imported : imports) {
            List<AggregatedCandle> all = Arrays.stream(Timeframe.values())
                    .flatMap(timeframe -> aggregate(imported, timeframe, SOURCE_OFFSET).stream())
                    .toList();
            candles.put(imported.symbol(), all);
            Map<String, Map<String, Integer>> byTimeframe = new LinkedHashMap<>();
            for (Timeframe timeframe : Timeframe.values()) {
                Map<String, Integer> states = Arrays.stream(aggregate(imported, timeframe, SOURCE_OFFSET)
                                .toArray(AggregatedCandle[]::new))
                        .collect(Collectors.groupingBy(value -> value.state().name(), LinkedHashMap::new,
                                Collectors.summingInt(value -> 1)));
                byTimeframe.put(timeframe.name(), states);
                completeCounts.put(imported.symbol() + "." + timeframe.name(),
                        states.getOrDefault(AggregationState.COMPLETE.name(), 0));
            }
            candleQuality.put(imported.symbol(), byTimeframe);
        }
        List<FeatureSnapshot> snapshots = new ArrayList<>();
        List<ObservedMarketOutcome> outcomes = new ArrayList<>();
        for (ImportResult imported : imports) {
            List<AggregatedCandle> m15 = aggregate(imported, Timeframe.M15, SOURCE_OFFSET).stream()
                    .filter(value -> value.state() == AggregationState.COMPLETE).toList();
            for (int index = 0; index < m15.size(); index++) {
                AggregatedCandle reference = m15.get(index);
                List<AggregatedCandle> history = m15.subList(Math.max(0, index - HISTORY_LIMIT + 1), index + 1);
                history = contiguousTail(history);
                UUID executionId = stableId("histdata:execution:" + imported.symbol() + ":" + reference.openTime());
                String contextFingerprint = ArtifactFingerprint.ofInputs(List.of(
                        SOURCE_PROVIDER, imported.symbol(), SOURCE_SEMANTICS, PRICE_SIDE,
                        reference.closeTime().toString(), historyFingerprint(history))).value();
                ProducedArtifact analysis = analyze(imported, reference, history, executionId, contextFingerprint);
                if (analysis == null) continue;
                FeatureProjectionV1.ProjectionResult projection = FeatureProjectionV2.project(
                        executionId, imported.marketId(), reference.closeTime(), EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                        contextFingerprint, List.of(analysis), List.of(), List.of(), RUN_TIME);
                if (!volatilityFeaturesAvailable(projection)) continue;
                snapshots.add(new FeatureSnapshot(stableId("histdata:snapshot:" + imported.symbol() + ":" + reference.openTime()),
                        executionId, imported.marketId(), reference.closeTime(), EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                        FeatureProjectionV2.VERSION, contextFingerprint, projection.values(), projection.groups(),
                        projection.provenance(), projection.fingerprint(), RUN_TIME));
                ObservedMarketOutcome outcome = outcome(imported, m15, index, reference, executionId);
                if (outcome != null) outcomes.add(outcome);
            }
        }
        DatasetBuildResult dataset = buildDataset(snapshots, outcomes);
        ResearchDatasetBundleExporter exporter = new ResearchDatasetBundleExporter(new ObjectMapper().findAndRegisterModules());
        exporter.export(dataset, outputDirectory);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        for (ImportResult imported : imports) {
            for (Timeframe timeframe : Timeframe.values()) {
                List<AggregatedCandle> values = aggregate(imported, timeframe, SOURCE_OFFSET);
                writeJsonLines(mapper, outputDirectory.resolve(imported.symbol().replace("/", "") + "-" + timeframe + ".jsonl"), values);
            }
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", SOURCE_PROVIDER);
        metadata.put("sourceSemantics", SOURCE_SEMANTICS);
        metadata.put("sourceTimezone", "EST / UTC-05:00, fixed offset, no daylight saving adjustment");
        metadata.put("priceSide", PRICE_SIDE);
        metadata.put("normalizationVersion", NORMALIZATION_VERSION);
        metadata.put("markets", imports.stream().map(ImportResult::symbol).toList());
        metadata.put("featureProjectionVersion", FeatureProjectionV2.VERSION);
        metadata.put("featureSnapshotCount", snapshots.size());
        metadata.put("eligibleFeatureRows", dataset.manifest().eligibleCount());
        metadata.put("excludedFeatureRows", dataset.manifest().excludedCount());
        metadata.put("datasetFingerprint", dataset.manifest().datasetFingerprint());
        metadata.put("candleQuality", candleQuality);
        metadata.put("quality", imports.stream().collect(Collectors.toMap(ImportResult::symbol, HistDataOfflinePipeline::qualityMetadata,
                (left, right) -> left, LinkedHashMap::new)));
        Files.writeString(outputDirectory.resolve("source-metadata.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata));
        return new PipelineResult(imports, completeCounts, snapshots.size(), dataset.manifest().eligibleCount(),
                dataset.manifest().excludedCount(), dataset.manifest().datasetFingerprint(), outputDirectory);
    }

    private static ProducedArtifact analyze(ImportResult imported, AggregatedCandle reference,
                                            List<AggregatedCandle> history, UUID executionId, String contextFingerprint) {
        List<OhlcPoint> points = history.stream().map(value -> new OhlcPoint(value.openTime(), value.closeTime(),
                value.open(), value.high(), value.low(), value.close(), null)).toList();
        HistoricalOhlcContext context = new HistoricalOhlcContext(imported.marketId(), "M15", points);
        ArtifactIdentity identity = new ArtifactIdentity(ProductionArtifactTypes.OHLC_HISTORY.value(),
                "histdata-offline-import", NORMALIZATION_VERSION);
        ArtifactFingerprint inputFingerprint = ArtifactFingerprint.ofInputs(List.of(
                imported.sourceFile().getFileName().toString(), imported.symbol(), historyFingerprint(history)));
        ArtifactScope scope = ArtifactScope.publicMarket(imported.marketId(), "M15", AnalysisExecutionMode.PASSIVE);
        StoredArtifact input = new StoredArtifact(
                new ArtifactCacheKey(identity, scope, ArtifactFingerprint.empty(), inputFingerprint), context,
                ArtifactFreshness.validUntil(reference.closeTime(), reference.closeTime().plusSeconds(1), NORMALIZATION_VERSION),
                new ArtifactProvenance("histdata-offline-import", NORMALIZATION_VERSION, executionId,
                        reference.closeTime(), Set.of(), Set.of(), contextFingerprint, inputFingerprint.value())
                        .withEvidenceContext(EvidenceAcquisitionPolicy.STRICT_OBSERVED, points.size(), reference.closeTime()),
                AnalysisResultQuality.COMPLETE, ProductionArtifactTypes.V1);
        com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement requirement =
                new com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement(
                ProductionArtifactTypes.OHLC_HISTORY, ProductionArtifactTypes.V1,
                VersionCompatibilityMode.EXACT, true, ArtifactCardinality.ONE, false);
        CapabilityContext capabilityContext = new CapabilityContext(executionId, executionId,
                Map.of(requirement, List.of(input)), Set.of(), Map.of(), List.of(), () -> false, reference.closeTime());
        CapabilityResult result = new OhlcRangeAnalysisCapability().execute(capabilityContext);
        return result.artifacts().isEmpty() ? null : result.artifacts().getFirst();
    }

    private static List<AggregatedCandle> contiguousTail(List<AggregatedCandle> history) {
        if (history.isEmpty()) return List.of();
        int start = history.size() - 1;
        for (int index = history.size() - 2; index >= 0; index--) {
            if (!history.get(index).closeTime().equals(history.get(index + 1).openTime())) break;
            start = index;
        }
        return history.subList(start, history.size());
    }

    private static ObservedMarketOutcome outcome(ImportResult imported, List<AggregatedCandle> candles, int index,
                                                 AggregatedCandle reference, UUID executionId) {
        int count = (int) (OUTCOME_HORIZON.toMinutes() / 15);
        if (index + count >= candles.size()) return null;
        List<AggregatedCandle> future = candles.subList(index + 1, index + 1 + count);
        for (int futureIndex = 0; futureIndex < future.size(); futureIndex++) {
            AggregatedCandle previous = futureIndex == 0 ? reference : future.get(futureIndex - 1);
            if (!future.get(futureIndex).openTime().equals(previous.closeTime())) return null;
        }
        OutcomeHorizon horizon = new OutcomeHorizon(OUTCOME_HORIZON);
        String referenceFingerprint = ArtifactFingerprint.ofInputs(List.of(imported.symbol(), reference.openTime().toString(),
                reference.open().toPlainString(), reference.high().toPlainString(), reference.low().toPlainString(),
                reference.close().toPlainString())).value();
        List<OutcomeFutureObservation> observations = future.stream().map(value -> new OutcomeFutureObservation(
                value.openTime(), value.closeTime(), value.high(), value.low(), value.close(),
                ArtifactFingerprint.ofInputs(List.of(value.openTime().toString(), value.close().toPlainString())).value())).toList();
        String futureFingerprint = ArtifactFingerprint.ofInputs(observations.stream()
                .map(OutcomeFutureObservation::observationFingerprint).toList()).value();
        MarketOutcomeCalculator.CalculatedOutcome calculated = new MarketOutcomeCalculator().calculate(
                new OutcomeReferenceEvidence(reference.close(), reference.closeTime(), reference.closeTime(),
                        referenceFingerprint, NORMALIZATION_VERSION), observations, count, count, List.of(),
                futureFingerprint, reference.closeTime(), horizon);
        String definition = MarketOutcomeCalculator.DEFINITION_VERSION;
        String fingerprint = MarketOutcomeFingerprint.fingerprint(definition, imported.marketId(), reference.closeTime(), horizon,
                referenceFingerprint, futureFingerprint, ObservedMarketOutcomeStatus.AVAILABLE,
                calculated.referencePrice(), calculated.finalPrice(), calculated.futureHigh(), calculated.futureLow(),
                calculated.forwardReturn(), calculated.futureHighReturn(), calculated.futureLowReturn(), calculated.futureRange(),
                NORMALIZATION_VERSION, List.of(NORMALIZATION_VERSION));
        return new ObservedMarketOutcome(imported.marketId(), reference.closeTime(), horizon,
                reference.closeTime().plus(OUTCOME_HORIZON), ObservedMarketOutcomeStatus.AVAILABLE, null,
                calculated.referencePrice(), referenceFingerprint, calculated.finalPrice(), calculated.futureHigh(),
                calculated.futureLow(), calculated.forwardReturn(), calculated.futureHighReturn(), calculated.futureLowReturn(),
                calculated.futureRange(), count, count, NORMALIZATION_VERSION, List.of(NORMALIZATION_VERSION),
                futureFingerprint, definition, fingerprint, RUN_TIME, executionId);
    }

    private static DatasetBuildResult buildDataset(List<FeatureSnapshot> snapshots, List<ObservedMarketOutcome> outcomes) {
        Map<ResearchFeatureSnapshotKey, String> selectedSnapshots = snapshots.stream().collect(Collectors.toMap(
                value -> new ResearchFeatureSnapshotKey(value.marketId(), value.evaluationCutoff()),
                FeatureSnapshot::fingerprint, (left, right) -> left, LinkedHashMap::new));
        Map<ResearchOutcomeKey, String> selectedOutcomes = outcomes.stream().collect(Collectors.toMap(
                value -> new ResearchOutcomeKey(value.marketId(), value.evaluationCutoff(), value.horizon()),
                ObservedMarketOutcome::outcomeFingerprint, (left, right) -> left, LinkedHashMap::new));
        Instant start = snapshots.stream().map(FeatureSnapshot::evaluationCutoff).min(Instant::compareTo).orElse(RUN_TIME);
        // The Python validator parses ISO timestamps at microsecond precision.
        Instant end = snapshots.stream().map(FeatureSnapshot::evaluationCutoff).max(Instant::compareTo)
                .orElse(start).plusSeconds(1);
        ResearchPopulationSpec population = new ResearchPopulationSpec(
                "histdata-offline-v1", snapshots.stream().map(FeatureSnapshot::marketId).collect(Collectors.toSet()),
                start, end, FeatureProjectionV2.VERSION, EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                MarketOutcomeCalculator.DEFINITION_VERSION, Set.of(new OutcomeHorizon(OUTCOME_HORIZON)),
                ResearchSamplingCadence.ONE_PER_FEATURE_SNAPSHOT, Set.of(), selectedSnapshots,
                ResearchFeatureRequirements.fromNames("histdata-volatility-v1", List.of(
                        "ohlc.atr", "ohlc.normalized_atr", "ohlc.realized_volatility",
                        "ohlc.volatility_compression_ratio", "ohlc.directional_efficiency"), Set.of()), selectedOutcomes);
        return new ResearchDatasetBuilder(java.time.Clock.fixed(RUN_TIME, ZoneOffset.UTC)).build(population, snapshots, outcomes);
    }

    private static Map<String, Object> qualityMetadata(ImportResult result) {
        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("sourceRows", result.observations().size() + result.invalidRows() + result.malformedRows()
                + result.exactDuplicateRows() + result.conflictingDuplicateRows());
        quality.put("eligibleM1Rows", result.observations().size());
        quality.put("malformedRows", result.malformedRows());
        quality.put("invalidRows", result.invalidRows());
        quality.put("exactDuplicateRowsDeduplicated", result.exactDuplicateRows());
        quality.put("conflictingDuplicateRowsQuarantined", result.conflictingDuplicateRows());
        quality.put("chronologicalInversions", result.chronologicalInversions());
        quality.put("missingM1Slots", result.missingM1Slots());
        quality.put("sessionUncertainM1Slots", result.sessionUncertainM1Slots());
        quality.put("nonSessionM1Slots", result.nonSessionM1Slots());
        return quality;
    }

    private static void writeJsonLines(ObjectMapper mapper, Path path, List<AggregatedCandle> values) throws IOException {
        Files.write(path, values.stream().map(value -> {
            try {
                return mapper.writeValueAsString(value);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }).toList(), StandardCharsets.UTF_8);
    }

    private static String historyFingerprint(List<AggregatedCandle> candles) {
        return ArtifactFingerprint.ofInputs(candles.stream().map(value -> value.sourceOpenTime() + "|"
                + value.open() + "|" + value.high() + "|" + value.low() + "|" + value.close()).toList()).value();
    }

    private static boolean volatilityFeaturesAvailable(FeatureProjectionV1.ProjectionResult projection) {
        Set<String> required = Set.of("ohlc.atr", "ohlc.normalized_atr", "ohlc.realized_volatility",
                "ohlc.volatility_compression_ratio", "ohlc.directional_efficiency");
        return projection.values().stream()
                .filter(value -> required.contains(value.name()))
                .allMatch(value -> value.status() == FeatureMissingness.PRESENT);
    }

    private static boolean sameValues(M1Observation left, M1Observation right) {
        return left.open().equals(right.open()) && left.high().equals(right.high())
                && left.low().equals(right.low()) && left.close().equals(right.close());
    }

    private static boolean validOhlc(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        return open.signum() > 0 && high.signum() > 0 && low.signum() > 0 && close.signum() > 0
                && high.compareTo(low) >= 0 && open.compareTo(low) >= 0 && open.compareTo(high) <= 0
                && close.compareTo(low) >= 0 && close.compareTo(high) <= 0;
    }

    private static boolean weeklyClosureCandidate(LocalDateTime timestamp) {
        return (timestamp.getDayOfWeek() == DayOfWeek.FRIDAY && timestamp.getHour() >= 17)
                || timestamp.getDayOfWeek() == DayOfWeek.SATURDAY
                || (timestamp.getDayOfWeek() == DayOfWeek.SUNDAY && timestamp.getHour() < 17);
    }

    private static LocalDateTime bucketStart(LocalDateTime timestamp, int minutes) {
        int minuteOfDay = timestamp.getHour() * 60 + timestamp.getMinute();
        return timestamp.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay - Math.floorMod(minuteOfDay, minutes));
    }

    private static UUID marketId(String symbol) {
        return stableId("histdata:market:" + symbol);
    }

    private static UUID stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void requireSymbol(String symbol) {
        if (symbol == null || !symbol.matches("[A-Z]{3}/[A-Z]{3}")) {
            throw new IllegalArgumentException("canonical symbol is required");
        }
    }

    public static void main(String[] args) throws Exception {
        Path input = args.length > 0 ? Path.of(args[0]) : Path.of("histData");
        Path output = args.length > 1 ? Path.of(args[1]) : input.resolve("derived");
        PipelineResult result = run(input, output);
        System.out.printf(Locale.ROOT, "exported=%d eligible=%d fingerprint=%s output=%s%n",
                result.featureSnapshotCount(), result.eligibleFeatureRows(), result.datasetFingerprint(), result.outputDirectory());
    }
}
