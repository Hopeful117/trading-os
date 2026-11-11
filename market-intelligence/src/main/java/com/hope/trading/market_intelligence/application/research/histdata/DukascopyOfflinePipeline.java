package com.hope.trading.market_intelligence.application.research.histdata;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.io.BufferedReader;
import java.io.IOException;
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

    public record RejectedObservation(int lineNumber, String reason) {
    }

    public record ImportResult(
            UUID marketId,
            String symbol,
            Path sourceFile,
            List<M1Observation> observations,
            List<RejectedObservation> qualityEvents,
            Set<Instant> quarantinedTimestamps,
            int malformedRows,
            int invalidRows,
            int exactDuplicateRows,
            int conflictingDuplicateRows,
            List<Gap> missingIntervals,
            String sourceFingerprint
    ) {
        public ImportResult {
            observations = List.copyOf(observations);
            qualityEvents = List.copyOf(qualityEvents);
            quarantinedTimestamps = Set.copyOf(quarantinedTimestamps);
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
        int malformed = 0;
        int invalid = 0;
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
                    events.add(new RejectedObservation(lineNumber, "MALFORMED_FIELD_COUNT"));
                    continue;
                }
                try {
                    Instant openTime = Instant.parse(fields[0]);
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
                        events.add(new RejectedObservation(lineNumber, "INVALID_TIMESTAMP_OR_OHLCV"));
                    }
                } catch (RuntimeException exception) {
                    malformed++;
                    events.add(new RejectedObservation(lineNumber, "MALFORMED_TIMESTAMP_OR_NUMBER"));
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
                eligible.add(values.getFirst());
            } else {
                conflictingDuplicates += values.size();
                quarantined.add(entry.getKey());
                values.forEach(value -> events.add(new RejectedObservation(value.lineNumber(),
                        "CONFLICTING_DUPLICATE_TIMESTAMP_QUARANTINED")));
            }
        }
        eligible.sort(Comparator.comparing(M1Observation::openTime));
        List<Gap> gaps = gaps(eligible);
        return new ImportResult(marketId, symbol, sourceFile, eligible, events, quarantined, malformed, invalid,
                exactDuplicates, conflictingDuplicates, gaps, sourceFingerprint(sourceFile, symbol, eligible, events));
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
                    material.add(value.symbol() + "|" + value.sourceFingerprint());
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
        events.stream().map(value -> value.lineNumber() + "|" + value.reason()).sorted().forEach(material::add);
        return ArtifactFingerprint.ofInputs(material).value();
    }
}
