package com.hope.trading.market_intelligence.application.research.histdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DukascopyOfflinePipelineTest {
    private static final UUID MARKET_ID = UUID.nameUUIDFromBytes("dukascopy-test".getBytes());
    private static final Instant START = Instant.parse("2025-10-01T00:00:00Z");
    private static final Instant END = Instant.parse("2025-10-01T00:05:00Z");

    @Test
    void readsUtcCsvRejectsConflictsAndReportsGaps(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("eurusd-2025-10.csv");
        Files.writeString(source, String.join("\n",
                "timestamp,open,high,low,close,volume",
                row("2025-10-01T00:00:00Z", "1", "2", "0.5", "1.5"),
                row("2025-10-01T00:00:00Z", "1", "2", "0.5", "1.5"),
                row("2025-10-01T00:02:00Z", "1", "2", "0.5", "1.5"),
                row("2025-10-01T00:02:00Z", "1", "2.1", "0.5", "1.5"),
                row("2025-10-01T00:04:00Z", "1", "2", "0.5", "1.5"),
                "2025-10-01T00:05:00Z,1,2,0.5,1.5,10\n"));

        var result = DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, END);

        assertThat(result.observations()).extracting(DukascopyOfflinePipeline.M1Observation::openTime)
                .containsExactly(START, Instant.parse("2025-10-01T00:04:00Z"));
        assertThat(result.exactDuplicateRows()).isEqualTo(1);
        assertThat(result.conflictingDuplicateRows()).isEqualTo(2);
        assertThat(result.missingIntervals()).containsExactly(new DukascopyOfflinePipeline.Gap(
                Instant.parse("2025-10-01T00:01:00Z"), Instant.parse("2025-10-01T00:04:00Z")));
        assertThat(result.observations().getFirst().openTime()).isEqualTo(START);
        assertThat(result.sourceFingerprint()).hasSize(64);
    }

    @Test
    void rejectsNonUtcMinuteOrInvalidHeader(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("bad.csv");
        Files.writeString(source, "timestamp,open,high,low,close,volume\n"
                + "2025-10-01T00:00:30Z,1,2,0.5,1.5,10\n");

        var result = DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, END);
        assertThat(result.observations()).isEmpty();
        assertThat(result.invalidRows()).isEqualTo(1);

        Files.writeString(source, "wrong\n");
        assertThatThrownBy(() -> DukascopyOfflinePipeline.read(source, MARKET_ID, "EUR/USD", START, END))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header");
    }

    @Test
    void detectsCrossFileOverlapAndFingerprintsAssembly(@TempDir Path temp) throws Exception {
        Path first = temp.resolve("first.csv");
        Path second = temp.resolve("second.csv");
        String header = "timestamp,open,high,low,close,volume\n";
        Files.writeString(first, header + row("2025-10-01T00:00:00Z", "1", "2", "0.5", "1.5") + "\n");
        Files.writeString(second, header + row("2025-10-01T00:00:00Z", "1", "2", "0.5", "1.5") + "\n");
        var left = DukascopyOfflinePipeline.read(first, MARKET_ID, "EUR/USD", START, END);
        var right = DukascopyOfflinePipeline.read(second, MARKET_ID, "EUR/USD", START, END);

        var assembly = DukascopyOfflinePipeline.assemble(List.of(left, right));

        assertThat(assembly.crossFileOverlaps()).containsExactly(START);
        assertThat(assembly.datasetFingerprint()).hasSize(64);
    }

    private static String row(String timestamp, String open, String high, String low, String close) {
        return timestamp + "," + open + "," + high + "," + low + "," + close + ",10";
    }
}
