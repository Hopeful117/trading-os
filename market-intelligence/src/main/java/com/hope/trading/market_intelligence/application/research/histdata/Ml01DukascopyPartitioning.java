package com.hope.trading.market_intelligence.application.research.histdata;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Deterministic three-way chronological partitioning for the Dukascopy ML-01 bundle. */
public final class Ml01DukascopyPartitioning {
    public static final String POLICY_ID = "ML01_DUKASCOPY_DEPENDENCY_PURGE_V1";

    private Ml01DukascopyPartitioning() {
    }

    public record Scenario(String name, Instant trainEndExclusive, Instant validationEndExclusive,
                           Instant testEndExclusive) {
        public Scenario {
            if (name == null || name.isBlank() || trainEndExclusive == null
                    || validationEndExclusive == null || testEndExclusive == null
                    || !trainEndExclusive.isBefore(validationEndExclusive)
                    || !validationEndExclusive.isBefore(testEndExclusive)) {
                throw new IllegalArgumentException("three chronological UTC boundaries are required");
            }
        }
    }

    public record DependencyWindow(Instant cutoff, Instant futureEnd) {
        public DependencyWindow {
            Objects.requireNonNull(cutoff, "cutoff");
            if (futureEnd != null && futureEnd.isBefore(cutoff)) {
                throw new IllegalArgumentException("future end cannot precede cutoff");
            }
        }
    }

    public record Assignment(String partition, String exclusionReason) {
        public Assignment {
            Objects.requireNonNull(partition, "partition");
            if (exclusionReason != null && exclusionReason.isBlank()) {
                throw new IllegalArgumentException("exclusion reason must be null or non-blank");
            }
        }
    }

    public record Result(List<Row> rows, Map<String, Long> counts, String fingerprint, boolean ready) {
        public Result {
            rows = List.copyOf(rows);
            counts = Map.copyOf(counts);
        }

        public record Row(Ml01DukascopyPreparation.Row source, String partition,
                          String exclusionReason, List<Integer> crossingHorizons) {
            public Row {
                Objects.requireNonNull(source, "source");
                Objects.requireNonNull(partition, "partition");
                crossingHorizons = List.copyOf(crossingHorizons);
            }
        }
    }

    public static Assignment assign(Instant cutoff, Scenario scenario, Duration embargo) {
        Objects.requireNonNull(cutoff, "cutoff");
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(embargo, "embargo");
        if (embargo.isNegative()) throw new IllegalArgumentException("embargo cannot be negative");
        if (cutoff.isBefore(scenario.trainEndExclusive())) return new Assignment("TRAIN", null);
        if (cutoff.isBefore(scenario.validationEndExclusive())) {
            return inEmbargo(cutoff, scenario.trainEndExclusive(), embargo)
                    ? new Assignment("VALIDATION", "EMBARGO_AFTER_TRAIN")
                    : new Assignment("VALIDATION", null);
        }
        if (cutoff.isBefore(scenario.testEndExclusive())) {
            return inEmbargo(cutoff, scenario.validationEndExclusive(), embargo)
                    ? new Assignment("TEST", "EMBARGO_AFTER_VALIDATION")
                    : new Assignment("TEST", null);
        }
        return new Assignment("OUT_OF_RANGE", "OUTSIDE_PARTITION_BOUNDS");
    }

    public static Result partition(List<Ml01DukascopyPreparation.Row> sourceRows,
                                   Scenario scenario, Duration embargo) {
        Objects.requireNonNull(sourceRows, "source rows");
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(embargo, "embargo");
        List<Result.Row> rows = new ArrayList<>();
        for (Ml01DukascopyPreparation.Row row : sourceRows.stream()
                .sorted(Comparator.comparing((Ml01DukascopyPreparation.Row value) -> value.bucket().closeTime())
                        .thenComparing(value -> value.bucket().symbol())
                        .thenComparing(value -> value.bucket().openTime())).toList()) {
            Assignment assignment = assign(row.bucket().closeTime(), scenario, embargo);
            List<Integer> crossing = crossingHorizons(row, assignment.partition(), scenario);
            String reason = assignment.exclusionReason();
            if (reason == null && !row.jointAvailable()) reason = "JOINT_OUTCOME_UNAVAILABLE";
            if (reason == null && !crossing.isEmpty()) reason = "FUTURE_WINDOW_CROSSES_PROTECTED_BOUNDARY";
            rows.add(new Result.Row(row, assignment.partition(), reason, crossing));
        }
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(Result.Row::partition,
                LinkedHashMap::new, Collectors.counting()));
        long train = rows.stream().filter(value -> "TRAIN".equals(value.partition()) && value.exclusionReason() == null).count();
        long validation = rows.stream().filter(value -> "VALIDATION".equals(value.partition()) && value.exclusionReason() == null).count();
        long test = rows.stream().filter(value -> "TEST".equals(value.partition()) && value.exclusionReason() == null).count();
        counts.put("TRAIN_EFFECTIVE", train);
        counts.put("VALIDATION_EFFECTIVE", validation);
        counts.put("TEST_EFFECTIVE", test);
        String fingerprint = ArtifactFingerprint.ofInputs(List.of(POLICY_ID, scenario.name(),
                scenario.trainEndExclusive().toString(), scenario.validationEndExclusive().toString(),
                scenario.testEndExclusive().toString(), embargo.toString(), rows.stream()
                        .map(value -> value.source().rowFingerprint() + "|" + value.partition() + "|"
                                + value.exclusionReason() + "|" + value.crossingHorizons()).toList().toString())).value();
        return new Result(rows, counts, fingerprint, train > 0 && validation > 0 && test > 0);
    }

    public static void requireNoProtectedBoundaryCrossing(Result result) {
        result.rows().stream()
                .filter(row -> ("TRAIN".equals(row.partition()) || "VALIDATION".equals(row.partition()))
                        && row.exclusionReason() == null && !row.crossingHorizons().isEmpty())
                .findFirst()
                .ifPresent(row -> {
                    throw new IllegalStateException("retained row crosses protected boundary: "
                            + row.source().rowFingerprint() + " horizons=" + row.crossingHorizons());
                });
    }

    private static List<Integer> crossingHorizons(Ml01DukascopyPreparation.Row row, String partition,
                                                   Scenario scenario) {
        Instant boundary = switch (partition) {
            case "TRAIN" -> scenario.trainEndExclusive();
            case "VALIDATION" -> scenario.validationEndExclusive();
            default -> null;
        };
        if (boundary == null) return List.of();
        return row.outcomes().values().stream()
                .filter(outcome -> "AVAILABLE".equals(outcome.status()) && outcome.futureEnd() != null
                        && !outcome.futureEnd().isBefore(boundary))
                .map(Ml01DukascopyPreparation.Outcome::horizon)
                .sorted().toList();
    }

    private static boolean inEmbargo(Instant cutoff, Instant boundary, Duration embargo) {
        return !embargo.isZero() && !cutoff.isBefore(boundary)
                && cutoff.isBefore(boundary.plus(embargo));
    }
}
