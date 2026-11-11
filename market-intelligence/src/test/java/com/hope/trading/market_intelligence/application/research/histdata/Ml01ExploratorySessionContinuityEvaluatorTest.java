package com.hope.trading.market_intelligence.application.research.histdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Ml01ExploratorySessionContinuityEvaluatorTest {
    private static final Ml01ExploratorySessionContinuityEvaluator EVALUATOR =
            new Ml01ExploratorySessionContinuityEvaluator();

    @Test
    void acceptsFridaySeventeenToSundaySeventeen() {
        var observations = weekendWindow(LocalDateTime.of(2026, 9, 4, 16, 0), 17);

        var result = EVALUATOR.evaluate(observations, 1, observations.size() - 2);

        assertThat(result.classification()).withFailMessage(result.reason()).isEqualTo(
                Ml01ExploratorySessionContinuityEvaluator.Classification.ASSUMED_SESSION_TRANSITION);
        assertThat(result.actualElapsed().toHours()).isEqualTo(48);
    }

    @Test
    void acceptsLatestSundayEighteenReopening() {
        var observations = weekendWindow(LocalDateTime.of(2026, 9, 4, 16, 0), 18);

        var result = EVALUATOR.evaluate(observations, 1, observations.size() - 2);

        assertThat(result.classification()).withFailMessage(result.reason()).isEqualTo(
                Ml01ExploratorySessionContinuityEvaluator.Classification.ASSUMED_SESSION_TRANSITION);
        assertThat(result.actualElapsed().toHours()).isEqualTo(49);
    }

    @Test
    void rejectsLateReopeningEarlyFridayCloseAndDatasetEdges() {
        var late = weekendWindow(LocalDateTime.of(2026, 9, 4, 16, 0), 19);
        var early = weekendWindow(LocalDateTime.of(2026, 9, 4, 15, 0), 17);
        var valid = weekendWindow(LocalDateTime.of(2026, 9, 4, 16, 0), 17);

        assertThat(EVALUATOR.evaluate(late, 1, late.size() - 2).classification())
                .isEqualTo(Ml01ExploratorySessionContinuityEvaluator.Classification.UNEXPLAINED_GAP);
        assertThat(EVALUATOR.evaluate(early, 1, early.size() - 2).classification())
                .isEqualTo(Ml01ExploratorySessionContinuityEvaluator.Classification.UNEXPLAINED_GAP);
        assertThat(EVALUATOR.evaluate(valid, 0, valid.size() - 2).classification())
                .isEqualTo(Ml01ExploratorySessionContinuityEvaluator.Classification.UNEXPLAINED_GAP);
    }

    @Test
    void rejectsActivityOrInvalidQualityInsideTheAssumedClosure() {
        var activity = weekendWindow(LocalDateTime.of(2026, 9, 4, 16, 0), 17);
        activity.set(2, observation(candle("2026-09-05T00:00:00Z", "2026-09-05T01:00:00Z",
                        HistDataOfflinePipeline.AggregationState.INCOMPLETE, 10), rejectedDecision()));
        var invalid = weekendWindow(LocalDateTime.of(2026, 9, 4, 16, 0), 17);
        invalid.set(2, observation(candle("2026-09-05T00:00:00Z", "2026-09-05T01:00:00Z",
                        HistDataOfflinePipeline.AggregationState.INVALID, 0), rejectedDecision()));

        assertThat(EVALUATOR.evaluate(activity, 1, activity.size() - 2).classification())
                .isEqualTo(Ml01ExploratorySessionContinuityEvaluator.Classification.UNEXPLAINED_GAP);
        assertThat(EVALUATOR.evaluate(invalid, 1, invalid.size() - 2).classification())
                .isEqualTo(Ml01ExploratorySessionContinuityEvaluator.Classification.INVALID_OBSERVATION);
    }

    @Test
    void permitsAtMostOneAssumedTransitionAndKeepsResearchClassificationSeparate() {
        var transition = new Ml01ExploratorySessionContinuityEvaluator.Transition(
                Ml01ExploratorySessionContinuityEvaluator.Classification.ASSUMED_SESSION_TRANSITION,
                1, 2, null, null, null, java.time.Duration.ofHours(48), "test");
        var contiguous = new Ml01ExploratorySessionContinuityEvaluator.Transition(
                Ml01ExploratorySessionContinuityEvaluator.Classification.CONTIGUOUS,
                2, 3, null, null, null, java.time.Duration.ZERO, "test");
        var second = new Ml01ExploratorySessionContinuityEvaluator.Transition(
                Ml01ExploratorySessionContinuityEvaluator.Classification.ASSUMED_SESSION_TRANSITION,
                3, 4, null, null, null, java.time.Duration.ofHours(48), "test");

        assertThat(EVALUATOR.permits(List.of(transition, contiguous))).isTrue();
        assertThat(EVALUATOR.permits(List.of(transition, second))).isFalse();
        assertThat(Ml01ExploratorySessionContinuityEvaluator.Classification.ASSUMED_SESSION_TRANSITION)
                .isNotEqualTo(Ml01HistDataPreparation.TransitionClassification.VERIFIED_CLOSURE);
        assertThat(Ml01ExploratorySessionContinuityEvaluator.POLICY_VERSION)
                .isEqualTo(Ml01HistDataPreparation.CONTINUITY_POLICY_VERSION);
    }

    private static List<Ml01ExploratorySessionContinuityEvaluator.Observation> weekendWindow(
            LocalDateTime previousOpen, int reopeningHour) {
        List<Ml01ExploratorySessionContinuityEvaluator.Observation> result = new ArrayList<>();
        result.add(observation(candle(previousOpen.minusHours(1).toInstant(ZoneOffset.ofHours(-5)),
                previousOpen.toInstant(ZoneOffset.ofHours(-5)), HistDataOfflinePipeline.AggregationState.COMPLETE, 60), eligibleDecision()));
        result.add(observation(candle(previousOpen.toInstant(ZoneOffset.ofHours(-5)),
                previousOpen.plusHours(1).toInstant(ZoneOffset.ofHours(-5)), HistDataOfflinePipeline.AggregationState.COMPLETE, 60), eligibleDecision()));
        LocalDateTime interior = previousOpen.plusHours(1);
        LocalDateTime nextOpen = LocalDateTime.of(2026, 9, 6, reopeningHour, 0);
        while (interior.plusHours(1).isBefore(nextOpen)) {
            result.add(observation(candle(interior.toInstant(ZoneOffset.ofHours(-5)),
                    interior.plusHours(1).toInstant(ZoneOffset.ofHours(-5)),
                    HistDataOfflinePipeline.AggregationState.SESSION_UNCERTAIN, 0), rejectedDecision()));
            interior = interior.plusHours(1);
        }
        result.add(observation(candle(nextOpen.toInstant(ZoneOffset.ofHours(-5)),
                nextOpen.plusHours(1).toInstant(ZoneOffset.ofHours(-5)), HistDataOfflinePipeline.AggregationState.COMPLETE, 60), eligibleDecision()));
        result.add(observation(candle(nextOpen.plusHours(1).toInstant(ZoneOffset.ofHours(-5)),
                nextOpen.plusHours(2).toInstant(ZoneOffset.ofHours(-5)), HistDataOfflinePipeline.AggregationState.COMPLETE, 60), eligibleDecision()));
        return result;
    }

    private static Ml01ExploratorySessionContinuityEvaluator.Observation observation(
            HistDataOfflinePipeline.AggregatedCandle candle, Ml01H1AdmissibilityDecision decision) {
        return new Ml01ExploratorySessionContinuityEvaluator.Observation(candle, decision);
    }

    private static HistDataOfflinePipeline.AggregatedCandle candle(String open, String close,
                                                                    HistDataOfflinePipeline.AggregationState state,
                                                                    int observed) {
        return candle(Instant.parse(open), Instant.parse(close), state, observed);
    }

    private static HistDataOfflinePipeline.AggregatedCandle candle(Instant open, Instant close,
                                                                    HistDataOfflinePipeline.AggregationState state,
                                                                    int observed) {
        return new HistDataOfflinePipeline.AggregatedCandle(UUID.randomUUID(), "EUR/USD",
                HistDataOfflinePipeline.Timeframe.H1, LocalDateTime.ofInstant(open, ZoneOffset.UTC), open, close,
                new BigDecimal("1.1000"), new BigDecimal("1.1010"), new BigDecimal("1.0990"),
                new BigDecimal("1.1005"), null, state, 60, observed, "test.csv");
    }

    private static Ml01H1AdmissibilityDecision eligibleDecision() {
        return new Ml01H1AdmissibilityDecision("ML01_HISTDATA_EXPLORATORY_V1", "v1",
                Ml01H1ResearchQualityState.FULL_OBSERVED, List.of(), null, "EUR/USD", "test", "fingerprint",
                LocalDateTime.of(2026, 9, 4, 16, 0), Instant.parse("2026-09-04T22:00:00Z"), 60, 60,
                List.of(), true, true, 0, false, false, false, false,
                HistDataOfflinePipeline.AggregationState.COMPLETE, List.of("CONTIGUOUS"));
    }

    private static Ml01H1AdmissibilityDecision rejectedDecision() {
        return new Ml01H1AdmissibilityDecision("ML01_HISTDATA_EXPLORATORY_V1", "v1",
                Ml01H1ResearchQualityState.REJECTED,
                List.of(Ml01H1RejectionReason.INSUFFICIENT_VALID_M1_COUNT),
                Ml01H1RejectionReason.INSUFFICIENT_VALID_M1_COUNT.name(), "EUR/USD", "test", "fingerprint",
                LocalDateTime.of(2026, 9, 5, 0, 0), Instant.parse("2026-09-05T06:00:00Z"), 60, 0,
                List.of(), false, false, 60, false, false, false, false,
                HistDataOfflinePipeline.AggregationState.SESSION_UNCERTAIN, List.of("UNEXPLAINED_GAP"));
    }
}
