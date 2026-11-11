package com.hope.trading.market_intelligence.application.research.histdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class Ml01HistDataPreparationTest {
    private static final DateTimeFormatter SOURCE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss");

    @Test
    void preparesH1FeaturesAndValidCandleOutcomesWithoutScientificPartitionDefaults(@TempDir Path temp)
            throws Exception {
        Path input = writeSources(temp, 72);
        Path output = temp.resolve("ml01");

        var result = Ml01HistDataPreparation.prepare(input, output,
                Ml01HistDataPreparation.PartitionConfiguration.requiresApproval());

        assertThat(result.rows()).isEqualTo(144);
        assertThat(result.eligibleRows()).isGreaterThan(0);
        assertThat(result.readinessStatus()).isEqualTo("REQUIRES_SCIENTIFIC_APPROVAL");
        assertThat(Files.readString(output.resolve("readiness.json")))
                .contains("\"kmeansExecuted\" : false")
                .contains("SCIENTIFIC_PARTITION_PARAMETERS_UNAPPROVED");
        String rows = Files.readString(output.resolve("observations.jsonl"));
        assertThat(rows).contains("ohlc.normalized_atr")
                .contains("ohlc.realized_volatility")
                .contains("ohlc.volatility_compression_ratio")
                .contains("ohlc.directional_efficiency")
                .contains("\"horizon\":4")
                .contains("\"horizon\":12")
                .contains("\"horizon\":24");
    }

    @Test
    void recordsBoundaryPurgingAndEmbargoWithoutDroppingWithinPartitionOverlap(@TempDir Path temp)
            throws Exception {
        Path input = writeSources(temp, 96);
        Path output = temp.resolve("ml01-partitioned");
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        var configuration = new Ml01HistDataPreparation.PartitionConfiguration(
                start.plusHours(48).toInstant(ZoneOffset.ofHours(-5)),
                start.plusHours(80).toInstant(ZoneOffset.ofHours(-5)),
                start.plusHours(96).toInstant(ZoneOffset.ofHours(-5)),
                Duration.ofHours(2));

        var result = Ml01HistDataPreparation.prepare(input, output, configuration);

        assertThat(result.readinessStatus()).isEqualTo("PIPELINE_VALIDATED");
        String report = Files.readString(output.resolve("partition-manifest.json"));
        assertThat(report).contains("FORWARD_WINDOW_CROSSES_PARTITION_BOUNDARY")
                .contains("EMBARGO_AFTER_TRAIN")
                .contains("PERMITTED_AND_REPORTED_AS_STATISTICAL_DEPENDENCE");
    }

    @Test
    void acceptsVerifiedClosureButRejectsUnexplainedGap() {
        var previous = candle("2026-09-01T10:00:00Z", "2026-09-01T11:00:00Z");
        var next = candle("2026-09-01T12:00:00Z", "2026-09-01T13:00:00Z");
        var closure = new Ml01HistDataPreparation.ClosureEvidence(
                "EUR/USD", previous.closeTime(), next.openTime(), Instant.parse("2026-09-01T09:00:00Z"),
                "calendar", "v1", "closure-fingerprint");

        assertThat(Ml01HistDataPreparation.classifyTransition(previous, next,
                Instant.parse("2026-09-01T11:30:00Z"), List.of(closure)))
                .isEqualTo(Ml01HistDataPreparation.TransitionClassification.VERIFIED_CLOSURE);
        assertThat(Ml01HistDataPreparation.classifyTransition(previous, next,
                Instant.parse("2026-09-01T11:30:00Z"), List.of()))
                .isEqualTo(Ml01HistDataPreparation.TransitionClassification.UNEXPLAINED_GAP);
    }

    @Test
    void rejectsChronologicallyInvertedTransitionBeforeClosureMatching() {
        var previous = candle("2026-09-01T12:00:00Z", "2026-09-01T13:00:00Z");
        var next = candle("2026-09-01T11:00:00Z", "2026-09-01T12:00:00Z");
        var closure = new Ml01HistDataPreparation.ClosureEvidence(
                "EUR/USD", Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T14:00:00Z"),
                Instant.parse("2026-09-01T09:00:00Z"),
                "calendar", "v1", "closure-fingerprint");

        assertThat(Ml01HistDataPreparation.classifyTransition(previous, next,
                Instant.parse("2026-09-01T13:00:00Z"), List.of(closure)))
                .isEqualTo(Ml01HistDataPreparation.TransitionClassification.INVALID_OBSERVATION);
    }

    @Test
    void restrictsPreparationToTheAuthorizedMarketAndCutoffScope(@TempDir Path temp) throws Exception {
        Path input = writeSources(temp, 72);
        Path output = temp.resolve("ml01-scoped");
        UUID eurUsd = UUID.nameUUIDFromBytes("histdata:market:EUR/USD".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Instant start = Instant.parse("2026-09-01T05:00:00Z");
        Instant end = Instant.parse("2026-09-01T10:00:00Z");

        var result = Ml01HistDataPreparation.prepare(input, output,
                Ml01HistDataPreparation.PartitionConfiguration.requiresApproval(),
                new Ml01HistDataPreparation.PreparationScope(Set.of(eurUsd), start, end), List.of());

        assertThat(result.rows()).isEqualTo(4);
        assertThat(Files.readString(output.resolve("observations.jsonl")))
                .contains("EUR/USD")
                .doesNotContain("GBP/USD");
    }

    private static Path writeSources(Path directory, int hours) throws Exception {
        for (String symbol : List.of("EURUSD", "GBPUSD")) {
            StringBuilder content = new StringBuilder();
            LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
            for (int minute = 0; minute < hours * 60; minute++) {
                LocalDateTime timestamp = start.plusMinutes(minute);
                double base = 1.100000 + minute * 0.000001;
                content.append(SOURCE_FORMAT.format(timestamp)).append(';')
                        .append(String.format(java.util.Locale.ROOT, "%.6f", base)).append(';')
                        .append(String.format(java.util.Locale.ROOT, "%.6f", base + 0.000100)).append(';')
                        .append(String.format(java.util.Locale.ROOT, "%.6f", base - 0.000100)).append(';')
                        .append(String.format(java.util.Locale.ROOT, "%.6f", base + 0.000050)).append(";0\n");
            }
            Files.writeString(directory.resolve("DAT_ASCII_" + symbol + "_M1_202609.csv"), content);
        }
        return directory;
    }

    private static HistDataOfflinePipeline.AggregatedCandle candle(String open, String close) {
        Instant openTime = Instant.parse(open);
        Instant closeTime = Instant.parse(close);
        return new HistDataOfflinePipeline.AggregatedCandle(
                UUID.randomUUID(), "EUR/USD", HistDataOfflinePipeline.Timeframe.H1,
                LocalDateTime.ofInstant(openTime, ZoneOffset.UTC), openTime, closeTime,
                new BigDecimal("1.1000"), new BigDecimal("1.1010"), new BigDecimal("1.0990"),
                new BigDecimal("1.1005"), null, HistDataOfflinePipeline.AggregationState.COMPLETE,
                60, 60, "test.csv");
    }
}
