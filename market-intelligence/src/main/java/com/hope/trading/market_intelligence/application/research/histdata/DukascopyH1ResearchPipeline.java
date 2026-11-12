package com.hope.trading.market_intelligence.application.research.histdata;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DukascopyH1ResearchPipeline {
    private static final MathContext MC = MathContext.DECIMAL128;

    private DukascopyH1ResearchPipeline() {
    }

    public static List<DukascopyH1Bucket> aggregate(
            List<DukascopyOfflinePipeline.ImportResult> imports,
            Instant startInclusive,
            Instant endExclusive
    ) {
        Objects.requireNonNull(imports, "imports");
        Objects.requireNonNull(startInclusive, "start");
        Objects.requireNonNull(endExclusive, "end");
        if (!startInclusive.isBefore(endExclusive) || imports.isEmpty()) {
            throw new IllegalArgumentException("valid Dukascopy H1 aggregation scope is required");
        }
        String symbol = imports.getFirst().symbol();
        UUID marketId = imports.getFirst().marketId();
        if (imports.stream().anyMatch(value -> !symbol.equals(value.symbol()) || !marketId.equals(value.marketId()))) {
            throw new IllegalArgumentException("all imports must belong to one instrument");
        }

        Map<Instant, DukascopyOfflinePipeline.M1Observation> observations = new HashMap<>();
        Set<Instant> overlaps = new HashSet<>();
        for (var input : imports) {
            for (var observation : input.observations()) {
                if (observations.putIfAbsent(observation.openTime(), observation) != null) {
                    overlaps.add(observation.openTime());
                }
            }
        }
        String datasetFingerprint = DukascopyOfflinePipeline.assemble(imports).datasetFingerprint();

        List<DukascopyH1Bucket> result = new ArrayList<>();
        for (Instant hour = startInclusive.truncatedTo(ChronoUnit.HOURS);
             hour.isBefore(endExclusive); hour = hour.plus(1, ChronoUnit.HOURS)) {
            Instant bucketStart = hour;
            Instant close = bucketStart.plus(1, ChronoUnit.HOURS);
            List<DukascopyOfflinePipeline.M1Observation> hourRows = observations.entrySet().stream()
                    .filter(entry -> !entry.getKey().isBefore(bucketStart) && entry.getKey().isBefore(close))
                    .map(Map.Entry::getValue)
                    .sorted(Comparator.comparing(DukascopyOfflinePipeline.M1Observation::openTime))
                    .toList();
            Set<Instant> timestamps = hourRows.stream()
                    .map(DukascopyOfflinePipeline.M1Observation::openTime).collect(java.util.stream.Collectors.toSet());
            List<Instant> observed = timestamps.stream().sorted().toList();
            List<Instant> missing = new ArrayList<>();
            int maximumRun = 0;
            int currentRun = 0;
            for (int minute = 0; minute < 60; minute++) {
                Instant timestamp = bucketStart.plus(minute, ChronoUnit.MINUTES);
                if (timestamps.contains(timestamp)) {
                    currentRun = 0;
                } else {
                    missing.add(timestamp);
                    currentRun++;
                    maximumRun = Math.max(maximumRun, currentRun);
                }
            }
            boolean malformed = hasEvent(imports, bucketStart, close, true);
            boolean invalid = hasEvent(imports, bucketStart, close, false);
            boolean chronology = imports.stream().anyMatch(value -> value.chronologyAnomalyTimestamps().stream()
                    .anyMatch(timestamp -> !timestamp.isBefore(bucketStart) && timestamp.isBefore(close)));
            boolean conflict = imports.stream().anyMatch(value -> value.quarantinedTimestamps().stream()
                    .anyMatch(timestamp -> !timestamp.isBefore(bucketStart) && timestamp.isBefore(close)));
            boolean overlap = overlaps.stream().anyMatch(timestamp -> !timestamp.isBefore(bucketStart) && timestamp.isBefore(close));
            boolean unknownIntegrity = imports.stream().anyMatch(value -> value.unassignableMalformedRows() > 0);
            int exactDuplicates = countEvents(imports, bucketStart, close, true);
            int conflictingDuplicates = countEvents(imports, bucketStart, close, false);
            DukascopyH1Bucket.StrictState state = hourRows.isEmpty()
                    ? DukascopyH1Bucket.StrictState.EMPTY
                    : malformed || invalid || chronology || conflict || overlap || unknownIntegrity
                    ? DukascopyH1Bucket.StrictState.INVALID
                    : hourRows.size() == 60 ? DukascopyH1Bucket.StrictState.COMPLETE
                    : DukascopyH1Bucket.StrictState.PARTIAL;
            BigDecimal open = hourRows.isEmpty() ? null : hourRows.getFirst().open();
            BigDecimal closePrice = hourRows.isEmpty() ? null : hourRows.getLast().close();
            BigDecimal high = hourRows.stream().map(DukascopyOfflinePipeline.M1Observation::high)
                    .max(Comparator.naturalOrder()).orElse(null);
            BigDecimal low = hourRows.stream().map(DukascopyOfflinePipeline.M1Observation::low)
                    .min(Comparator.naturalOrder()).orElse(null);
            BigDecimal volume = hourRows.stream().map(DukascopyOfflinePipeline.M1Observation::volume)
                    .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, MC));
            String sourceFingerprint = ArtifactFingerprint.ofInputs(List.of(
                    DukascopyOfflinePipeline.SOURCE_PROVIDER, symbol, bucketStart.toString(),
                    datasetFingerprint, observed.toString(), missing.toString(), Integer.toString(exactDuplicates),
                    Integer.toString(conflictingDuplicates), Boolean.toString(malformed), Boolean.toString(invalid),
                    Boolean.toString(chronology), Boolean.toString(conflict), Boolean.toString(overlap),
                    Boolean.toString(unknownIntegrity),
                    hourRows.stream().map(value -> value.openTime() + "|" + value.open() + "|" + value.high()
                            + "|" + value.low() + "|" + value.close() + "|" + value.volume()).toList().toString(),
                    state.name())).value();
            result.add(new DukascopyH1Bucket(marketId, symbol, bucketStart, close, open, high, low, closePrice, volume,
                    60, hourRows.size(), observed, missing, timestamps.contains(bucketStart),
                    timestamps.contains(bucketStart.plus(59, ChronoUnit.MINUTES)), maximumRun, malformed, invalid,
                    conflict, chronology, overlap, unknownIntegrity, exactDuplicates, conflictingDuplicates, state,
                    switch (state) {
                        case COMPLETE -> "BUCKET_COMPLETE";
                        case PARTIAL -> "BUCKET_PARTIAL";
                        case EMPTY -> "BUCKET_EMPTY";
                        case INVALID -> "BUCKET_INVALID";
                        case UNKNOWN -> "BUCKET_UNKNOWN";
                    }, sourceFingerprint, imports.stream().map(value -> value.sourceFile().getFileName() + "="
                            + value.sourceFileChecksum()).sorted().reduce((left, right) -> left + ";" + right).orElse("unknown")));
        }
        return List.copyOf(result);
    }

    private static boolean hasEvent(List<DukascopyOfflinePipeline.ImportResult> imports, Instant start, Instant end,
                                    boolean malformed) {
        return imports.stream().anyMatch(value -> {
            Set<Instant> timestamps = malformed ? value.malformedTimestamps() : value.invalidTimestamps();
            return timestamps.stream().anyMatch(timestamp -> !timestamp.isBefore(start) && timestamp.isBefore(end));
        });
    }

    private static int countEvents(List<DukascopyOfflinePipeline.ImportResult> imports, Instant start, Instant end,
                                   boolean exact) {
        return imports.stream().mapToInt(value -> (exact ? value.exactDuplicateRowCounts()
                        : value.conflictingDuplicateRowCounts()).entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(start) && entry.getKey().isBefore(end))
                .mapToInt(Map.Entry::getValue).sum()).sum();
    }
}
