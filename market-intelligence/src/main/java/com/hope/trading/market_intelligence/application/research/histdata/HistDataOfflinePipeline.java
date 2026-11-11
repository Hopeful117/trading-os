package com.hope.trading.market_intelligence.application.research.histdata;

import com.hope.trading.market_intelligence.domain.artifact.*;

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
            int nonSessionM1Slots,
            Set<LocalDateTime> malformedTimestamps,
            Set<LocalDateTime> chronologyAnomalyTimestamps
    ) {
        public ImportResult {
            observations = List.copyOf(observations);
            qualityEvents = List.copyOf(qualityEvents);
            quarantinedTimestamps = Set.copyOf(quarantinedTimestamps);
            invalidTimestamps = Set.copyOf(invalidTimestamps);
            malformedTimestamps = Set.copyOf(malformedTimestamps);
            chronologyAnomalyTimestamps = Set.copyOf(chronologyAnomalyTimestamps);
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
        Set<LocalDateTime> malformedTimestamps = new HashSet<>();
        Set<LocalDateTime> chronologyAnomalyTimestamps = new HashSet<>();
        LocalDateTime previous = null;
        try (BufferedReader reader = Files.newBufferedReader(sourceFile, StandardCharsets.US_ASCII)) {
            String raw;
            int lineNumber = 0;
            while ((raw = reader.readLine()) != null) {
                lineNumber++;
                String[] fields = raw.split(";", -1);
                if (fields.length != 6) {
                    malformedRows++;
                    if (fields.length > 0) {
                        try {
                            malformedTimestamps.add(LocalDateTime.parse(fields[0], SOURCE_FORMAT));
                        } catch (RuntimeException ignored) {
                            // The malformed timestamp cannot be assigned to an H1 bucket.
                        }
                    }
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
                    if (fields.length > 0) {
                        try {
                            malformedTimestamps.add(LocalDateTime.parse(fields[0], SOURCE_FORMAT));
                        } catch (RuntimeException ignored) {
                            // The malformed timestamp cannot be assigned to an H1 bucket.
                        }
                    }
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
                if (previous != null && sourceTimestamp.isBefore(previous)) {
                    inversions++;
                    chronologyAnomalyTimestamps.add(previous);
                    chronologyAnomalyTimestamps.add(sourceTimestamp);
                }
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
                inversions, missing, uncertain, nonSession, malformedTimestamps, chronologyAnomalyTimestamps);
    }

    public static H1BucketQualityEvidence qualityEvidence(ImportResult input, AggregatedCandle candle) {
        Objects.requireNonNull(input, "import result");
        Objects.requireNonNull(candle, "candle");
        if (candle.timeframe() != Timeframe.H1) {
            throw new IllegalArgumentException("quality evidence requires an H1 candle");
        }

        Map<LocalDateTime, M1Observation> observations = input.observations().stream()
                .filter(value -> !value.sourceTimestamp().isBefore(candle.sourceOpenTime())
                        && value.sourceTimestamp().isBefore(candle.sourceOpenTime().plusHours(1)))
                .collect(Collectors.toMap(M1Observation::sourceTimestamp, value -> value, (left, right) -> left));
        List<LocalDateTime> missing = new ArrayList<>();
        int maximumMissingRun = 0;
        int currentMissingRun = 0;
        for (int minute = 0; minute < 60; minute++) {
            LocalDateTime timestamp = candle.sourceOpenTime().plusMinutes(minute);
            if (observations.containsKey(timestamp)) {
                currentMissingRun = 0;
            } else {
                missing.add(timestamp);
                currentMissingRun++;
                maximumMissingRun = Math.max(maximumMissingRun, currentMissingRun);
            }
        }
        return new H1BucketQualityEvidence(
                input.symbol(),
                "MI_LOCAL_HISTDATA:" + input.sourceFile().getFileName(),
                candle.sourceOpenTime(),
                candle.closeTime(),
                60,
                observations.size(),
                missing,
                observations.containsKey(candle.sourceOpenTime()),
                observations.containsKey(candle.sourceOpenTime().plusMinutes(59)),
                maximumMissingRun,
                input.malformedTimestamps().stream().anyMatch(value -> inBucket(value, candle.sourceOpenTime())),
                input.invalidTimestamps().stream().anyMatch(value -> inBucket(value, candle.sourceOpenTime())),
                input.quarantinedTimestamps().stream().anyMatch(value -> inBucket(value, candle.sourceOpenTime())),
                input.chronologyAnomalyTimestamps().stream().anyMatch(value -> inBucket(value, candle.sourceOpenTime())),
                candle.state(),
                localStrictContinuityClassifications(candle),
                sourceFingerprint(input),
                false);
    }

    private static List<String> localStrictContinuityClassifications(AggregatedCandle candle) {
        return switch (candle.state()) {
            case COMPLETE -> List.of("CONTIGUOUS");
            case INCOMPLETE -> List.of("UNEXPLAINED_GAP");
            case INVALID -> List.of("INVALID_OBSERVATION");
            case SESSION_UNCERTAIN -> List.of("UNKNOWN_CLOSURE_EVIDENCE");
        };
    }

    static String sourceFingerprint(ImportResult input) {
        List<String> material = new ArrayList<>();
        material.add(SOURCE_PROVIDER);
        material.add(SOURCE_SEMANTICS);
        material.add(input.marketId().toString());
        material.add(input.symbol());
        material.add(input.sourceFile().getFileName().toString());
        input.observations().stream()
                .sorted(Comparator.comparing(M1Observation::sourceTimestamp))
                .map(value -> value.sourceTimestamp() + "|" + value.open() + "|" + value.high()
                        + "|" + value.low() + "|" + value.close())
                .forEach(material::add);
        input.qualityEvents().stream()
                .map(value -> value.lineNumber() + "|" + value.reason() + "|" + value.raw())
                .sorted()
                .forEach(material::add);
        input.invalidTimestamps().stream().sorted().map(value -> "invalidTimestamp=" + value)
                .forEach(material::add);
        input.malformedTimestamps().stream().sorted().map(value -> "malformedTimestamp=" + value)
                .forEach(material::add);
        input.chronologyAnomalyTimestamps().stream().sorted().map(value -> "chronology=" + value)
                .forEach(material::add);
        input.quarantinedTimestamps().stream().sorted().map(value -> "quarantine=" + value)
                .forEach(material::add);
        material.add("malformed=" + input.malformedRows());
        material.add("invalid=" + input.invalidRows());
        material.add("exactDuplicates=" + input.exactDuplicateRows());
        material.add("conflictingDuplicates=" + input.conflictingDuplicateRows());
        return ArtifactFingerprint.ofInputs(material).value();
    }

    private static boolean inBucket(LocalDateTime value, LocalDateTime bucketStart) {
        return !value.isBefore(bucketStart) && value.isBefore(bucketStart.plusHours(1));
    }

    public record H1BucketQualityEvidence(
            String instrument,
            String sourceId,
            LocalDateTime sourceOpenTime,
            Instant closeTime,
            int expectedM1Count,
            int uniqueValidM1Count,
            List<LocalDateTime> missingMinutePositions,
            boolean firstMinutePresent,
            boolean lastMinutePresent,
            int maximumConsecutiveMissingMinuteRun,
            boolean malformedObservation,
            boolean invalidOhlc,
            boolean conflictingDuplicate,
            boolean chronologyAnomaly,
            AggregationState strictState,
            List<String> strictContinuityClassifications,
            String sourceFingerprint,
            boolean provenanceIncomplete
    ) {
        public H1BucketQualityEvidence {
            Objects.requireNonNull(instrument, "instrument");
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(sourceOpenTime, "sourceOpenTime");
            Objects.requireNonNull(closeTime, "closeTime");
            Objects.requireNonNull(missingMinutePositions, "missingMinutePositions");
            Objects.requireNonNull(strictState, "strictState");
            Objects.requireNonNull(strictContinuityClassifications, "strictContinuityClassifications");
            if (expectedM1Count < 0 || uniqueValidM1Count < 0 || maximumConsecutiveMissingMinuteRun < 0) {
                throw new IllegalArgumentException("quality counts cannot be negative");
            }
            if (uniqueValidM1Count > expectedM1Count) {
                throw new IllegalArgumentException("valid M1 count cannot exceed expected count");
            }
            missingMinutePositions = missingMinutePositions.stream().sorted().toList();
            strictContinuityClassifications = strictContinuityClassifications.stream().sorted().toList();
        }
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

}
