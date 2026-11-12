package com.hope.trading.market_intelligence.application.research.histdata;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the provider-neutral CSV emitted by the Dukascopy acquisition utility.
 * Dukascopy timestamps are already UTC; no HistData source offset is applied.
 */
public final class DukascopyOfflinePipeline {
    public static final String SOURCE_PROVIDER = "DUKASCOPY";
    public static final String PRICE_SIDE = "BID";
    public static final String NORMALIZATION_VERSION = "dukascopy-m1-normalization-v1";
    private static final String HEADER = "timestamp,open,high,low,close,volume";

    private DukascopyOfflinePipeline() {
    }

    public record M1Observation(
            String symbol,
            Path sourceFile,
            int lineNumber,
            Instant openTime,
            Instant closeTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume
    ) {
    }

    public record Gap(Instant startInclusive, Instant endExclusive) {
        public Gap {
            if (startInclusive == null || endExclusive == null || !startInclusive.isBefore(endExclusive)) {
                throw new IllegalArgumentException("gap bounds must be chronological");
            }
        }
    }

    public record RejectedObservation(int lineNumber, Instant timestamp, String reason, String contentFingerprint) {
    }

    public record ImportResult(
            UUID marketId,
            String symbol,
            Path sourceFile,
            List<M1Observation> observations,
            List<RejectedObservation> qualityEvents,
            Set<Instant> quarantinedTimestamps,
            Set<Instant> malformedTimestamps,
            Set<Instant> invalidTimestamps,
            Set<Instant> chronologyAnomalyTimestamps,
            Set<Instant> exactDuplicateTimestamps,
            Map<Instant, Integer> exactDuplicateRowCounts,
            Map<Instant, Integer> conflictingDuplicateRowCounts,
            int unassignableMalformedRows,
            int malformedRows,
            int invalidRows,
            int exactDuplicateRows,
            int conflictingDuplicateRows,
            List<Gap> missingIntervals,
            String sourceFingerprint,
            String sourceFileChecksum
    ) {
        public ImportResult {
            observations = List.copyOf(observations);
            qualityEvents = List.copyOf(qualityEvents);
            quarantinedTimestamps = Set.copyOf(quarantinedTimestamps);
            malformedTimestamps = Set.copyOf(malformedTimestamps);
            invalidTimestamps = Set.copyOf(invalidTimestamps);
            chronologyAnomalyTimestamps = Set.copyOf(chronologyAnomalyTimestamps);
            exactDuplicateTimestamps = Set.copyOf(exactDuplicateTimestamps);
            exactDuplicateRowCounts = Map.copyOf(exactDuplicateRowCounts);
            conflictingDuplicateRowCounts = Map.copyOf(conflictingDuplicateRowCounts);
            missingIntervals = List.copyOf(missingIntervals);
        }
    }

    public record AssemblyResult(List<M1Observation> observations, Set<Instant> crossFileOverlaps,
                                 String datasetFingerprint) {
        public AssemblyResult {
            observations = List.copyOf(observations);
            crossFileOverlaps = Set.copyOf(crossFileOverlaps);
        }
    }

    public static ImportResult read(Path sourceFile, UUID marketId, String symbol,
                                    Instant startInclusive, Instant endExclusive) throws IOException {
        Objects.requireNonNull(sourceFile, "source file");
        Objects.requireNonNull(marketId, "market ID");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(startInclusive, "start");
        Objects.requireNonNull(endExclusive, "end");
        if (!startInclusive.isBefore(endExclusive)) throw new IllegalArgumentException("invalid bounds");

        List<M1Observation> parsed = new ArrayList<>();
        List<RejectedObservation> events = new ArrayList<>();
        Set<Instant> malformedTimestamps = new HashSet<>();
        Set<Instant> invalidTimestamps = new HashSet<>();
        Set<Instant> chronologyAnomalyTimestamps = new HashSet<>();
        Set<Instant> exactDuplicateTimestamps = new HashSet<>();
        Map<Instant, Integer> exactDuplicateRowCounts = new HashMap<>();
        Map<Instant, Integer> conflictingDuplicateRowCounts = new HashMap<>();
        int malformed = 0;
        int unassignableMalformed = 0;
        int invalid = 0;
        Instant previousInputTimestamp = null;
        try (BufferedReader reader = Files.newBufferedReader(sourceFile, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!HEADER.equals(header)) throw new IllegalArgumentException("invalid Dukascopy CSV header");
            String raw;
            int lineNumber = 1;
            while ((raw = reader.readLine()) != null) {
                lineNumber++;
                String[] fields = raw.split(",", -1);
                if (fields.length != 6) {
                    malformed++;
                    Instant timestamp = parseTimestamp(fields);
                    if (timestamp != null) malformedTimestamps.add(timestamp);
                    if (timestamp == null) unassignableMalformed++;
                    events.add(new RejectedObservation(lineNumber, timestamp, "MALFORMED_FIELD_COUNT",
                            ArtifactFingerprint.ofInputs(List.of(raw)).value()));
                    continue;
                }
                try {
                    Instant openTime = Instant.parse(fields[0]);
                    if (previousInputTimestamp != null && openTime.isBefore(previousInputTimestamp)) {
                        chronologyAnomalyTimestamps.add(previousInputTimestamp);
                        chronologyAnomalyTimestamps.add(openTime);
                    }
                    previousInputTimestamp = openTime;
                    BigDecimal open = new BigDecimal(fields[1]);
                    BigDecimal high = new BigDecimal(fields[2]);
                    BigDecimal low = new BigDecimal(fields[3]);
                    BigDecimal close = new BigDecimal(fields[4]);
                    BigDecimal volume = new BigDecimal(fields[5]);
                    if (!openTime.isBefore(startInclusive) && openTime.isBefore(endExclusive)
                            && openTime.getEpochSecond() % 60 == 0
                            && validOhlcv(open, high, low, close, volume)) {
                        parsed.add(new M1Observation(symbol, sourceFile, lineNumber, openTime,
                                openTime.plusSeconds(60), open, high, low, close, volume));
                    } else {
                        invalid++;
                        invalidTimestamps.add(openTime);
                        events.add(new RejectedObservation(lineNumber, openTime, "INVALID_TIMESTAMP_OR_OHLCV",
                                ArtifactFingerprint.ofInputs(List.of(raw)).value()));
                    }
                } catch (RuntimeException exception) {
                    malformed++;
                    Instant timestamp = parseTimestamp(fields);
                    if (timestamp != null) malformedTimestamps.add(timestamp);
                    if (timestamp == null) unassignableMalformed++;
                    events.add(new RejectedObservation(lineNumber, timestamp, "MALFORMED_TIMESTAMP_OR_NUMBER",
                            ArtifactFingerprint.ofInputs(List.of(raw)).value()));
                }
            }
        }

        Map<Instant, List<M1Observation>> grouped = new HashMap<>();
        parsed.forEach(value -> grouped.computeIfAbsent(value.openTime(), ignored -> new ArrayList<>()).add(value));
        List<M1Observation> eligible = new ArrayList<>();
        Set<Instant> quarantined = new HashSet<>();
        int exactDuplicates = 0;
        int conflictingDuplicates = 0;
        for (var entry : grouped.entrySet()) {
            List<M1Observation> values = entry.getValue();
            if (values.size() == 1) {
                eligible.add(values.getFirst());
            } else if (values.stream().allMatch(value -> sameValues(values.getFirst(), value))) {
                exactDuplicates += values.size() - 1;
                exactDuplicateTimestamps.add(entry.getKey());
                exactDuplicateRowCounts.put(entry.getKey(), values.size() - 1);
                eligible.add(values.getFirst());
            } else {
                conflictingDuplicates += values.size();
                quarantined.add(entry.getKey());
                conflictingDuplicateRowCounts.put(entry.getKey(), values.size());
                values.forEach(value -> events.add(new RejectedObservation(value.lineNumber(), value.openTime(),
                        "CONFLICTING_DUPLICATE_TIMESTAMP_QUARANTINED",
                        ArtifactFingerprint.ofInputs(List.of(value.openTime().toString(), value.open().toString(),
                                value.high().toString(), value.low().toString(), value.close().toString(),
                                value.volume().toString())).value())));
            }
        }
        eligible.sort(Comparator.comparing(M1Observation::openTime));
        List<Gap> gaps = gaps(eligible);
        return new ImportResult(marketId, symbol, sourceFile, eligible, events, quarantined,
                malformedTimestamps, invalidTimestamps, chronologyAnomalyTimestamps, exactDuplicateTimestamps,
                exactDuplicateRowCounts, conflictingDuplicateRowCounts, unassignableMalformed, malformed, invalid,
                exactDuplicates, conflictingDuplicates, gaps,
                sourceFingerprint(sourceFile, symbol, eligible, events), sha256(sourceFile));
    }

    public static AssemblyResult assemble(List<ImportResult> imports) {
        Objects.requireNonNull(imports, "imports");
        List<M1Observation> all = imports.stream().flatMap(value -> value.observations().stream())
                .sorted(Comparator.comparing(M1Observation::openTime)).toList();
        Set<Instant> overlaps = new HashSet<>();
        Map<Instant, M1Observation> seen = new HashMap<>();
        all.forEach(value -> {
            if (seen.putIfAbsent(value.openTime(), value) != null) overlaps.add(value.openTime());
        });
        List<String> material = new ArrayList<>(List.of(SOURCE_PROVIDER, PRICE_SIDE, NORMALIZATION_VERSION));
        imports.stream().sorted(Comparator.comparing(ImportResult::symbol).thenComparing(value -> value.sourceFile().toString()))
                .forEach(value -> {
                    material.add(value.symbol() + "|" + value.sourceFingerprint() + "|" + value.sourceFileChecksum());
                    value.observations().forEach(observation -> material.add(observation.openTime().toString()));
                });
        return new AssemblyResult(all, overlaps, ArtifactFingerprint.ofInputs(material).value());
    }

    private static List<Gap> gaps(List<M1Observation> observations) {
        List<Gap> result = new ArrayList<>();
        for (int index = 1; index < observations.size(); index++) {
            Instant previous = observations.get(index - 1).openTime();
            Instant current = observations.get(index).openTime();
            if (Duration.between(previous, current).compareTo(Duration.ofMinutes(1)) > 0) {
                result.add(new Gap(previous.plusSeconds(60), current));
            }
        }
        return result;
    }

    private static boolean validOhlcv(BigDecimal open, BigDecimal high, BigDecimal low,
                                      BigDecimal close, BigDecimal volume) {
        return open.signum() > 0 && high.signum() > 0 && low.signum() > 0 && close.signum() > 0
                && volume.signum() >= 0 && high.compareTo(open.max(close)) >= 0
                && low.compareTo(open.min(close)) <= 0 && low.compareTo(high) <= 0;
    }

    private static boolean sameValues(M1Observation left, M1Observation right) {
        return left.open().compareTo(right.open()) == 0 && left.high().compareTo(right.high()) == 0
                && left.low().compareTo(right.low()) == 0 && left.close().compareTo(right.close()) == 0
                && left.volume().compareTo(right.volume()) == 0;
    }

    private static String sourceFingerprint(Path sourceFile, String symbol, List<M1Observation> observations,
                                            List<RejectedObservation> events) {
        List<String> material = new ArrayList<>(List.of(SOURCE_PROVIDER, PRICE_SIDE, NORMALIZATION_VERSION,
                sourceFile.getFileName().toString(), symbol));
        observations.forEach(value -> material.add(value.openTime() + "|" + value.open() + "|" + value.high()
                + "|" + value.low() + "|" + value.close() + "|" + value.volume()));
        events.stream().map(value -> value.lineNumber() + "|" + value.timestamp() + "|" + value.reason()
                + "|" + value.contentFingerprint()).sorted().forEach(material::add);
        return ArtifactFingerprint.ofInputs(material).value();
    }

    private static String sha256(Path sourceFile) throws IOException {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(sourceFile)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static Instant parseTimestamp(String[] fields) {
        if (fields.length == 0) return null;
        try {
            return Instant.parse(fields[0]);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
