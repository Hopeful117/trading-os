package com.hope.trading.market_intelligence.application.research.histdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Ml01H1AdmissibilityEvaluatorTest {
    private final Ml01H1AdmissibilityEvaluator evaluator = new Ml01H1AdmissibilityEvaluator();

    @ParameterizedTest
    @ValueSource(ints = {54, 55, 59, 60})
    void evaluatesCoverageThresholdsDeterministically(int validCount) {
        var decision = evaluator.evaluate(evidence(validCount, true, true, false, false, false, false, false));

        if (validCount == 54) {
            assertThat(decision.state()).isEqualTo(Ml01H1ResearchQualityState.REJECTED);
            assertThat(decision.rejectionReasons())
                    .contains(Ml01H1RejectionReason.INSUFFICIENT_VALID_M1_COUNT);
        } else if (validCount == 60) {
            assertThat(decision.state()).isEqualTo(Ml01H1ResearchQualityState.FULL_OBSERVED);
        } else {
            assertThat(decision.state()).isEqualTo(Ml01H1ResearchQualityState.PARTIAL_EXPLORATORY);
        }
        assertThat(decision.profileName()).isEqualTo("ML01_HISTDATA_EXPLORATORY_V1");
        assertThat(decision.profileVersion()).isEqualTo("v1");
        assertThat(decision.eligible()).isEqualTo(validCount >= 55);
    }

    @Test
    void rejectsMissingBoundaryMinutes() {
        assertThat(evaluator.evaluate(evidence(59, false, true, false, false, false, false, false)).state())
                .isEqualTo(Ml01H1ResearchQualityState.REJECTED);
        assertThat(evaluator.evaluate(evidence(59, true, false, false, false, false, false, false)).state())
                .isEqualTo(Ml01H1ResearchQualityState.REJECTED);
    }

    @Test
    void rejectsKnownIntegrityFailuresWithStableReasons() {
        var decision = evaluator.evaluate(evidence(59, true, true, true, true, true, true, false));

        assertThat(decision.state()).isEqualTo(Ml01H1ResearchQualityState.REJECTED);
        assertThat(decision.rejectionReasons()).containsExactly(
                Ml01H1RejectionReason.MALFORMED_OBSERVATION,
                Ml01H1RejectionReason.CONFLICTING_DUPLICATE,
                Ml01H1RejectionReason.INVALID_OHLC,
                Ml01H1RejectionReason.CHRONOLOGY_ANOMALY);
        assertThat(decision.primaryRejectionReason()).isEqualTo("MALFORMED_OBSERVATION");
    }

    @Test
    void unknownProvenanceIsNotEligible() {
        var decision = evaluator.evaluate(evidence(60, true, true, false, false, false, false, true));

        assertThat(decision.state()).isEqualTo(Ml01H1ResearchQualityState.UNKNOWN_QUALITY);
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.rejectionReasons()).contains(Ml01H1RejectionReason.UNKNOWN_PROVENANCE);
    }

    @Test
    void blankFingerprintIsUnknownEvenWhenOtherProvenanceIsPresent() {
        var input = evidence(60, true, true, false, false, false, false, false);
        var incompleteFingerprint = new HistDataOfflinePipeline.H1BucketQualityEvidence(
                input.instrument(), input.sourceId(), input.sourceOpenTime(), input.closeTime(),
                input.expectedM1Count(), input.uniqueValidM1Count(), input.missingMinutePositions(),
                input.firstMinutePresent(), input.lastMinutePresent(), input.maximumConsecutiveMissingMinuteRun(),
                input.malformedObservation(), input.invalidOhlc(), input.conflictingDuplicate(),
                input.chronologyAnomaly(), input.strictState(), input.strictContinuityClassifications(), "", false);

        assertThat(evaluator.evaluate(incompleteFingerprint).state())
                .isEqualTo(Ml01H1ResearchQualityState.UNKNOWN_QUALITY);
    }

    @Test
    void contradictoryMissingnessIsUnknown() {
        var input = evidence(60, true, true, false, false, false, false, false);
        var contradictory = new HistDataOfflinePipeline.H1BucketQualityEvidence(
                input.instrument(), input.sourceId(), input.sourceOpenTime(), input.closeTime(),
                input.expectedM1Count(), input.uniqueValidM1Count(),
                List.of(input.sourceOpenTime().plusMinutes(10)), input.firstMinutePresent(),
                input.lastMinutePresent(), 0, input.malformedObservation(), input.invalidOhlc(),
                input.conflictingDuplicate(), input.chronologyAnomaly(), input.strictState(),
                input.strictContinuityClassifications(), input.sourceFingerprint(), false);

        assertThat(evaluator.evaluate(contradictory).state())
                .isEqualTo(Ml01H1ResearchQualityState.UNKNOWN_QUALITY);
        assertThat(evaluator.evaluate(contradictory).rejectionReasons())
                .contains(Ml01H1RejectionReason.CONTRADICTORY_EVIDENCE);
    }

    @Test
    void contradictoryCloseTimeIsUnknown() {
        var input = evidence(60, true, true, false, false, false, false, false);
        var contradictory = new HistDataOfflinePipeline.H1BucketQualityEvidence(
                input.instrument(), input.sourceId(), input.sourceOpenTime(), input.closeTime().plusSeconds(60),
                input.expectedM1Count(), input.uniqueValidM1Count(), input.missingMinutePositions(),
                input.firstMinutePresent(), input.lastMinutePresent(), input.maximumConsecutiveMissingMinuteRun(),
                input.malformedObservation(), input.invalidOhlc(), input.conflictingDuplicate(),
                input.chronologyAnomaly(), input.strictState(), input.strictContinuityClassifications(),
                input.sourceFingerprint(), false);

        assertThat(evaluator.evaluate(contradictory).state())
                .isEqualTo(Ml01H1ResearchQualityState.UNKNOWN_QUALITY);
        assertThat(evaluator.evaluate(contradictory).rejectionReasons())
                .containsExactly(Ml01H1RejectionReason.CONTRADICTORY_EVIDENCE);
    }

    @Test
    void preservesStrictStateAndContinuityMetadata() {
        var input = evidence(59, true, true, false, false, false, false, false);
        var withStrictMetadata = new HistDataOfflinePipeline.H1BucketQualityEvidence(
                input.instrument(), input.sourceId(), input.sourceOpenTime(), input.closeTime(),
                input.expectedM1Count(), input.uniqueValidM1Count(), input.missingMinutePositions(),
                input.firstMinutePresent(), input.lastMinutePresent(), input.maximumConsecutiveMissingMinuteRun(),
                input.malformedObservation(), input.invalidOhlc(), input.conflictingDuplicate(),
                input.chronologyAnomaly(), HistDataOfflinePipeline.AggregationState.SESSION_UNCERTAIN,
                List.of("UNEXPLAINED_GAP"), input.sourceFingerprint(), false);

        var decision = evaluator.evaluate(withStrictMetadata);

        assertThat(decision.state()).isEqualTo(Ml01H1ResearchQualityState.PARTIAL_EXPLORATORY);
        assertThat(decision.strictState()).isEqualTo(HistDataOfflinePipeline.AggregationState.SESSION_UNCERTAIN);
        assertThat(decision.strictContinuityClassifications()).containsExactly("UNEXPLAINED_GAP");
    }

    @Test
    void derivesBucketMissingnessAndConflictsFromParsedPipelineEvidence(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("EUR.csv");
        StringBuilder content = new StringBuilder();
        for (int minute = 0; minute < 60; minute++) {
            if (minute == 10) continue;
            content.append(row("20260901 00" + String.format("%02d", minute) + "00",
                    "1.100000", "1.101000", "1.099000", "1.100500")).append('\n');
        }
        Files.writeString(source, content);

        var imported = HistDataOfflinePipeline.read(source,
                java.util.UUID.randomUUID(), "EUR/USD", ZoneOffset.ofHours(-5));
        var candle = HistDataOfflinePipeline.aggregate(imported, HistDataOfflinePipeline.Timeframe.H1,
                ZoneOffset.ofHours(-5)).getFirst();
        var evidence = HistDataOfflinePipeline.qualityEvidence(imported, candle);

        assertThat(evidence.uniqueValidM1Count()).isEqualTo(59);
        assertThat(evidence.missingMinutePositions()).containsExactly(LocalDateTime.of(2026, 9, 1, 0, 10));
        assertThat(evidence.maximumConsecutiveMissingMinuteRun()).isEqualTo(1);
        assertThat(evaluator.evaluate(evidence).state()).isEqualTo(Ml01H1ResearchQualityState.PARTIAL_EXPLORATORY);
        assertThat(evidence.strictContinuityClassifications()).containsExactly("UNEXPLAINED_GAP");
    }

    @Test
    void attributesMalformedRowsWithValidTimestampsToTheirBucket(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("EUR.csv");
        StringBuilder content = new StringBuilder();
        for (int minute = 0; minute < 60; minute++) {
            String timestamp = "20260901 00" + String.format("%02d", minute) + "00";
            content.append(minute == 10 ? timestamp + ";1.100000\n"
                    : row(timestamp, "1.100000", "1.101000", "1.099000", "1.100500"));
            content.append('\n');
        }
        Files.writeString(source, content);

        var imported = HistDataOfflinePipeline.read(source,
                java.util.UUID.randomUUID(), "EUR/USD", ZoneOffset.ofHours(-5));
        var candle = HistDataOfflinePipeline.aggregate(imported, HistDataOfflinePipeline.Timeframe.H1,
                ZoneOffset.ofHours(-5)).getFirst();

        assertThat(HistDataOfflinePipeline.qualityEvidence(imported, candle).malformedObservation()).isTrue();
    }

    private static HistDataOfflinePipeline.H1BucketQualityEvidence evidence(
            int validCount, boolean first, boolean last, boolean malformed, boolean invalid,
            boolean conflict, boolean chronology, boolean provenanceIncomplete) {
        List<LocalDateTime> missing = new ArrayList<>();
        int missingCount = 60 - validCount;
        if (!first) {
            missing.add(LocalDateTime.of(2026, 9, 1, 0, 0));
        }
        int reservedLast = last ? 0 : 1;
        for (int minute = 1; missing.size() < missingCount - reservedLast && minute < 59; minute++) {
            missing.add(LocalDateTime.of(2026, 9, 1, 0, minute));
        }
        if (!last && missing.size() < missingCount) {
            missing.add(LocalDateTime.of(2026, 9, 1, 0, 59));
        }
        int maximumRun = 0;
        int currentRun = 0;
        for (int minute = 0; minute < 60; minute++) {
            if (missing.contains(LocalDateTime.of(2026, 9, 1, 0, minute))) {
                currentRun++;
                maximumRun = Math.max(maximumRun, currentRun);
            } else {
                currentRun = 0;
            }
        }
        return new HistDataOfflinePipeline.H1BucketQualityEvidence(
                "EUR/USD", "test.csv", LocalDateTime.of(2026, 9, 1, 0, 0),
                Instant.parse("2026-09-01T06:00:00Z"), 60, validCount, missing, first, last,
                maximumRun, malformed, invalid, conflict, chronology,
                validCount == 60 ? HistDataOfflinePipeline.AggregationState.COMPLETE
                        : HistDataOfflinePipeline.AggregationState.INCOMPLETE,
                List.of("CONTIGUOUS"), "test-fingerprint",
                provenanceIncomplete);
    }

    private static String row(String timestamp, String open, String high, String low, String close) {
        return timestamp + ";" + open + ";" + high + ";" + low + ";" + close + ";0";
    }
}
