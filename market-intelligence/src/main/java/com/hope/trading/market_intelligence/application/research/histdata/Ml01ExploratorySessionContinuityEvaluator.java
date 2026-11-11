package com.hope.trading.market_intelligence.application.research.histdata;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Evaluates the bounded, research-only HistData weekend convention from ADR-095.
 * It never changes the strict/local classification carried by an observation.
 */
public final class Ml01ExploratorySessionContinuityEvaluator {
    public static final String POLICY_ID = "ML01_HISTDATA_WEEKEND_SESSION_ASSUMPTION_V1";
    public static final String POLICY_VERSION = "ml01-h1-continuity-v2";
    public static final ZoneOffset SOURCE_OFFSET = ZoneOffset.ofHours(-5);
    public static final Duration MAXIMUM_GAP = Duration.ofHours(49);
    public static final int MAXIMUM_TRANSITIONS = 1;

    public enum Classification {
        CONTIGUOUS,
        VERIFIED_CLOSURE,
        ASSUMED_SESSION_TRANSITION,
        UNEXPLAINED_GAP,
        INVALID_OBSERVATION,
        UNKNOWN_QUALITY
    }

    public record Observation(HistDataOfflinePipeline.AggregatedCandle candle,
                              Ml01H1AdmissibilityDecision admissibility) {
        public Observation {
            if (candle == null || admissibility == null) {
                throw new IllegalArgumentException("session observation requires candle and admissibility");
            }
        }

        public boolean eligible() {
            return admissibility.eligible();
        }
    }

    public record Transition(Classification classification, int previousIndex, int nextIndex,
                             LocalDateTime nominalClosureStart, LocalDateTime nominalClosureEnd,
                             LocalDateTime latestPermittedReopening, Duration actualElapsed,
                             String reason) {
        public boolean assumed() {
            return classification == Classification.ASSUMED_SESSION_TRANSITION;
        }
    }

    public Transition evaluate(List<Observation> observations, int previousIndex, int nextIndex) {
        if (observations == null || previousIndex < 0 || nextIndex >= (observations == null ? 0 : observations.size())
                || previousIndex >= nextIndex) {
            return rejected(Classification.INVALID_OBSERVATION, previousIndex, nextIndex,
                    "invalid observation indexes");
        }
        Observation previous = observations.get(previousIndex);
        Observation next = observations.get(nextIndex);
        if (!previous.eligible() || !next.eligible()) {
            return rejected(Classification.INVALID_OBSERVATION, previousIndex, nextIndex,
                    "boundary observation is not exploratory-admissible");
        }
        var previousCandle = previous.candle();
        var nextCandle = next.candle();
        if (previousCandle.closeTime().isAfter(nextCandle.openTime())) {
            return rejected(Classification.INVALID_OBSERVATION, previousIndex, nextIndex,
                    "observations are chronologically inverted");
        }
        if (previousCandle.closeTime().equals(nextCandle.openTime())) {
            return new Transition(Classification.CONTIGUOUS, previousIndex, nextIndex,
                    null, null, null, Duration.ZERO, "exact H1 adjacency");
        }

        LocalDateTime previousLocal = previousCandle.closeTime().atOffset(SOURCE_OFFSET).toLocalDateTime();
        LocalDate friday = previousLocal.toLocalDate();
        LocalDateTime nominalStart = friday.atTime(17, 0);
        LocalDateTime nominalEnd = friday.plusDays(2).atTime(17, 0);
        LocalDateTime latestReopening = friday.plusDays(2).atTime(18, 0);
        LocalDateTime nextLocal = nextCandle.openTime().atOffset(SOURCE_OFFSET).toLocalDateTime();
        Duration elapsed = Duration.between(previousCandle.closeTime(), nextCandle.openTime());

        if (previousLocal.getDayOfWeek() != DayOfWeek.FRIDAY || !previousLocal.toLocalTime().equals(nominalStart.toLocalTime())
                || !nextLocal.toLocalDate().equals(friday.plusDays(2))
                || nextLocal.isBefore(nominalEnd) || nextLocal.isAfter(latestReopening)
                || elapsed.isNegative() || elapsed.compareTo(MAXIMUM_GAP) > 0) {
            return new Transition(Classification.UNEXPLAINED_GAP, previousIndex, nextIndex,
                    nominalStart, nominalEnd, latestReopening, elapsed,
                    "gap does not match the bounded weekend convention: previous=" + previousLocal
                            + ", next=" + nextLocal + ", elapsed=" + elapsed);
        }
        if (previousIndex == 0 || nextIndex == observations.size() - 1) {
            return new Transition(Classification.UNEXPLAINED_GAP, previousIndex, nextIndex,
                    nominalStart, nominalEnd, latestReopening, elapsed,
                    "dataset edge cannot qualify as a session transition");
        }

        for (int index = previousIndex + 1; index < nextIndex; index++) {
            Observation interior = observations.get(index);
            var candle = interior.candle();
            LocalDateTime openLocal = candle.openTime().atOffset(SOURCE_OFFSET).toLocalDateTime();
            if (candle.observedM1Count() > 0) {
                return rejected(Classification.UNEXPLAINED_GAP, previousIndex, nextIndex,
                        "observed activity exists inside the nominal closure");
            }
            if (!permittedEmptySessionObservation(interior, nominalStart, latestReopening)) {
                return rejected(Classification.INVALID_OBSERVATION, previousIndex, nextIndex,
                        "unknown, invalid or incompatible observation exists inside the assumed closure: state="
                                + interior.admissibility().state() + ", reasons="
                                + interior.admissibility().rejectionReasons());
            }
            if (openLocal.isBefore(nominalStart) || openLocal.isAfter(latestReopening)) {
                return rejected(Classification.UNEXPLAINED_GAP, previousIndex, nextIndex,
                        "unexplained interruption exists outside the nominal closure");
            }
        }
        return new Transition(Classification.ASSUMED_SESSION_TRANSITION, previousIndex, nextIndex,
                nominalStart, nominalEnd, latestReopening, elapsed,
                "bounded retrospective weekend assumption");
    }

    private boolean permittedEmptySessionObservation(Observation observation,
                                                     LocalDateTime nominalStart,
                                                     LocalDateTime latestReopening) {
        LocalDateTime openLocal = observation.candle().openTime().atOffset(SOURCE_OFFSET).toLocalDateTime();
        if (openLocal.isBefore(nominalStart) || openLocal.isAfter(latestReopening)) return false;
        if (observation.candle().state() != HistDataOfflinePipeline.AggregationState.SESSION_UNCERTAIN) return false;
        if (observation.admissibility().state() != Ml01H1ResearchQualityState.REJECTED) return false;
        return !observation.admissibility().rejectionReasons().isEmpty()
                && observation.admissibility().rejectionReasons().stream()
                .allMatch(reason -> reason == Ml01H1RejectionReason.INSUFFICIENT_VALID_M1_COUNT
                        || reason == Ml01H1RejectionReason.MISSING_FIRST_MINUTE
                        || reason == Ml01H1RejectionReason.MISSING_LAST_MINUTE);
    }

    public boolean permits(List<Transition> transitions) {
        return transitions != null && transitions.stream().noneMatch(value ->
                value.classification() != Classification.CONTIGUOUS
                        && value.classification() != Classification.VERIFIED_CLOSURE
                        && value.classification() != Classification.ASSUMED_SESSION_TRANSITION)
                && transitions.stream().filter(Transition::assumed).count() <= MAXIMUM_TRANSITIONS;
    }

    private Transition rejected(Classification classification, int previousIndex, int nextIndex, String reason) {
        return new Transition(classification, previousIndex, nextIndex, null, null, null, Duration.ZERO, reason);
    }
}
