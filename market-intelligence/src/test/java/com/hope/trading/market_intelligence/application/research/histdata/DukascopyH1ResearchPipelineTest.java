package com.hope.trading.market_intelligence.application.research.histdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DukascopyH1ResearchPipelineTest {
    private static final UUID MARKET_ID = UUID.nameUUIDFromBytes("dukascopy-h1-test".getBytes());
    private static final Instant START = Instant.parse("2025-10-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(3600);

    @Test
    void classifiesFullAndQualifiedPartialBucketsFromObservedUtcMinutes(@TempDir Path temp) throws Exception {
        Path source = writeSource(temp, 60, false, false);
        var imported = DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, END);
        var bucket = DukascopyH1ResearchPipeline.aggregate(java.util.List.of(imported), START, END).getFirst();

        assertThat(bucket.strictState()).isEqualTo(DukascopyH1Bucket.StrictState.COMPLETE);
        assertThat(new Ml01DukascopyH1AdmissibilityEvaluator().evaluate(bucket).state())
                .isEqualTo(DukascopyH1ResearchQualityState.FULL_OBSERVED);
        assertThat(bucket.maximumConsecutiveMissingMinuteRun()).isZero();

        source = writeSource(temp, 55, false, false);
        imported = DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, END);
        bucket = DukascopyH1ResearchPipeline.aggregate(java.util.List.of(imported), START, END).getFirst();

        var decision = new Ml01DukascopyH1AdmissibilityEvaluator().evaluate(bucket);
        assertThat(decision.state()).isEqualTo(DukascopyH1ResearchQualityState.PARTIAL_EXPLORATORY);
        assertThat(bucket.missingMinutePositions()).hasSize(5);
        assertThat(bucket.firstMinutePresent()).isTrue();
        assertThat(bucket.lastMinutePresent()).isTrue();
    }

    @Test
    void rejectsPartialBucketWhenBoundaryMinuteIsMissing(@TempDir Path temp) throws Exception {
        Path source = writeSource(temp, 58, true, true);
        var imported = DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, END);
        var bucket = DukascopyH1ResearchPipeline.aggregate(java.util.List.of(imported), START, END).getFirst();

        var decision = new Ml01DukascopyH1AdmissibilityEvaluator().evaluate(bucket);
        assertThat(decision.state()).isEqualTo(DukascopyH1ResearchQualityState.REJECTED);
        assertThat(decision.rejectionReasons()).contains(
                Ml01DukascopyH1RejectionReason.MISSING_FIRST_MINUTE,
                Ml01DukascopyH1RejectionReason.MISSING_LAST_MINUTE);
    }

    @Test
    void preservesEmptyBucketsAsContinuityBreakingEvidence(@TempDir Path temp) throws Exception {
        Path source = writeSource(temp, 60, false, false);
        var imported = DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, END);
        var buckets = DukascopyH1ResearchPipeline.aggregate(
                java.util.List.of(imported), START, END.plusSeconds(3600));

        assertThat(buckets).hasSize(2);
        assertThat(buckets.get(1).strictState()).isEqualTo(DukascopyH1Bucket.StrictState.EMPTY);
        assertThat(new Ml01DukascopyH1AdmissibilityEvaluator().evaluate(buckets.get(1)).state())
                .isEqualTo(DukascopyH1ResearchQualityState.REJECTED);
    }

    @Test
    void rejectsMalformedRowsEvenWhenTheirTimestampCanBeAssigned(@TempDir Path temp) throws Exception {
        Path source = writeSource(temp, 60, false, false);
        Files.writeString(source, Files.readString(source)
                + START.plusSeconds(15 * 60L) + ",1.1000,1.1010\n");

        var imported = DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, END);
        var bucket = DukascopyH1ResearchPipeline.aggregate(java.util.List.of(imported), START, END).getFirst();

        assertThat(bucket.malformedObservation()).isTrue();
        assertThat(bucket.strictState()).isEqualTo(DukascopyH1Bucket.StrictState.INVALID);
        assertThat(new Ml01DukascopyH1AdmissibilityEvaluator().evaluate(bucket).rejectionReasons())
                .contains(Ml01DukascopyH1RejectionReason.MALFORMED_OBSERVATION);
    }

    @Test
    void preservesDuplicateProvenanceAndStableBucketFingerprint(@TempDir Path temp) throws Exception {
        Path source = writeSource(temp, 60, false, false);
        Files.writeString(source, Files.readString(source)
                + START + ",1.1000,1.1010,1.0990,1.1005,1\n");

        var imported = DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, END);
        var first = DukascopyH1ResearchPipeline.aggregate(java.util.List.of(imported), START, END).getFirst();
        var second = DukascopyH1ResearchPipeline.aggregate(java.util.List.of(imported), START, END).getFirst();

        assertThat(first.exactDuplicateRows()).isEqualTo(1);
        assertThat(first.sourceFingerprint()).isEqualTo(second.sourceFingerprint());
        assertThat(first.observedMinutePositions()).hasSize(60);
    }

    @Test
    void failsClosedForMalformedRowsWithoutAnAssignableTimestamp(@TempDir Path temp) throws Exception {
        Path source = writeSource(temp, 60, false, false);
        Files.writeString(source, Files.readString(source) + "not-a-timestamp,1,2\n");

        var imported = DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, END);
        var bucket = DukascopyH1ResearchPipeline.aggregate(java.util.List.of(imported), START, END).getFirst();

        assertThat(imported.unassignableMalformedRows()).isEqualTo(1);
        assertThat(new Ml01DukascopyH1AdmissibilityEvaluator().evaluate(bucket).state())
                .isEqualTo(DukascopyH1ResearchQualityState.REJECTED);
    }

    @Test
    void writesByteStableBundleWithConfigurationAndBundleFingerprints(@TempDir Path temp) throws Exception {
        Path source = writeSource(temp, 60, false, false);
        var imported = DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, END);
        var result = Ml01DukascopyPreparation.prepare(java.util.List.of(imported), START, END);
        Path firstOutput = temp.resolve("first");
        Path secondOutput = temp.resolve("second");

        Ml01DukascopyPreparation.writeBundle(result, firstOutput);
        Ml01DukascopyPreparation.writeBundle(result, secondOutput);

        assertThat(result.qualityReport()).containsKeys("configurationFingerprint", "bundleFingerprint");
        assertThat(Files.readAllBytes(firstOutput.resolve("observations.jsonl")))
                .isEqualTo(Files.readAllBytes(secondOutput.resolve("observations.jsonl")));
        assertThat(Files.readAllBytes(firstOutput.resolve("dataset-manifest.json")))
                .isEqualTo(Files.readAllBytes(secondOutput.resolve("dataset-manifest.json")));
    }

    @Test
    void keepsTechnicallyAvailableJointRowsIneligibleWithoutScientificApproval(@TempDir Path temp) throws Exception {
        Path source = writeHourlySource(temp, 60);
        Instant end = START.plusSeconds(60 * 3600L);
        var imported = DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, end);
        var result = Ml01DukascopyPreparation.prepare(java.util.List.of(imported), START, end);

        assertThat(result.rows().stream().anyMatch(Ml01DukascopyPreparation.Row::jointAvailable)).isTrue();
        assertThat(result.rows()).noneMatch(Ml01DukascopyPreparation.Row::jointEligible);
        assertThat(result.readinessStatus()).isEqualTo("REQUIRES_SCIENTIFIC_APPROVAL");
        assertThat(result.qualityReport().get("profileSensitivity")).isNotNull();
    }

    private static Path writeSource(Path directory, int observed, boolean missingFirst, boolean missingLast)
            throws Exception {
        Path source = directory.resolve("eurusd.csv");
        StringBuilder content = new StringBuilder("timestamp,open,high,low,close,volume\n");
        int skipped = 60 - observed;
        for (int minute = 0; minute < 60; minute++) {
            if (missingFirst && minute == 0) continue;
            if (missingLast && minute == 59) continue;
            if (!missingFirst && !missingLast && skipped > 0 && minute >= 30 && minute < 30 + skipped) continue;
            String timestamp = START.plusSeconds(minute * 60L).toString();
            content.append(timestamp).append(",1.1000,1.1010,1.0990,1.1005,1\n");
        }
        Files.writeString(source, content);
        return source;
    }

    private static Path writeHourlySource(Path directory, int hours) throws Exception {
        Path source = directory.resolve("eurusd-series.csv");
        StringBuilder content = new StringBuilder("timestamp,open,high,low,close,volume\n");
        for (int hour = 0; hour < hours; hour++) {
            for (int minute = 0; minute < 60; minute++) {
                Instant timestamp = START.plusSeconds(hour * 3600L + minute * 60L);
                content.append(timestamp).append(",1.1000,1.1010,1.0990,1.1005,1\n");
            }
        }
        Files.writeString(source, content);
        return source;
    }
}
