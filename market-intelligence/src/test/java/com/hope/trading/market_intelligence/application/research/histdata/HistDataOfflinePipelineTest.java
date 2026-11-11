package com.hope.trading.market_intelligence.application.research.histdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HistDataOfflinePipelineTest {
    private static final UUID MARKET_ID = UUID.nameUUIDFromBytes("test-histdata".getBytes());

    @Test
    void parserQuarantinesConflictsDeduplicatesExactRowsAndSortsInversions(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("EUR.csv");
        Files.writeString(source, String.join("\n",
                row("20260901 000300", "1.100000", "1.101000", "1.099000", "1.100500"),
                row("20260901 000100", "1.100000", "1.101000", "1.099000", "1.100500"),
                row("20260901 000100", "1.100000", "1.101000", "1.099000", "1.100500"),
                row("20260901 000200", "1.100000", "1.102000", "1.098000", "1.101000"),
                row("20260901 000200", "1.100000", "1.102000", "1.098000", "1.101500"),
                "not;a;candle"));

        var result = HistDataOfflinePipeline.read(source, MARKET_ID, "EUR/USD", ZoneOffset.ofHours(-5));

        assertThat(result.observations()).extracting(HistDataOfflinePipeline.M1Observation::sourceTimestamp)
                .containsExactly(LocalDateTime.parse("2026-09-01T00:01"), LocalDateTime.parse("2026-09-01T00:03"));
        assertThat(result.exactDuplicateRows()).isEqualTo(1);
        assertThat(result.conflictingDuplicateRows()).isEqualTo(2);
        assertThat(result.quarantinedTimestamps()).containsExactly(LocalDateTime.parse("2026-09-01T00:02"));
        assertThat(result.malformedRows()).isEqualTo(1);
        assertThat(result.chronologicalInversions()).isEqualTo(1);
        assertThat(result.observations().getFirst().openTime()).isEqualTo(
                LocalDateTime.parse("2026-09-01T00:01").toInstant(ZoneOffset.ofHours(-5)));
    }

    @Test
    void aggregationRequiresEveryM1Contributor(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("EUR.csv");
        StringBuilder content = new StringBuilder();
        for (int minute = 0; minute < 15; minute++) {
            if (minute == 7) continue;
            String stamp = String.format("20260901 00%02d00", minute);
            content.append(row(stamp, "1.100000", "1.101000", "1.099000", "1.100500")).append('\n');
        }
        Files.writeString(source, content);

        var result = HistDataOfflinePipeline.read(source, MARKET_ID, "EUR/USD", ZoneOffset.ofHours(-5));
        var candle = HistDataOfflinePipeline.aggregate(result, HistDataOfflinePipeline.Timeframe.M15,
                ZoneOffset.ofHours(-5)).getFirst();

        assertThat(candle.state()).isEqualTo(HistDataOfflinePipeline.AggregationState.INCOMPLETE);
        assertThat(candle.expectedM1Count()).isEqualTo(15);
        assertThat(candle.observedM1Count()).isEqualTo(14);
        assertThat(candle.open()).isEqualByComparingTo("1.100000");
    }

    @Test
    void weeklyClosureCandidatesRemainSessionUncertain(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("EUR.csv");
        Files.writeString(source, String.join("\n",
                row("20260904 170000", "1.100000", "1.101000", "1.099000", "1.100500"),
                row("20260906 170000", "1.100000", "1.101000", "1.099000", "1.100500")));

        var result = HistDataOfflinePipeline.read(source, MARKET_ID, "EUR/USD", ZoneOffset.ofHours(-5));
        var candles = HistDataOfflinePipeline.aggregate(result, HistDataOfflinePipeline.Timeframe.H4,
                ZoneOffset.ofHours(-5));

        assertThat(candles).anyMatch(value -> value.state() == HistDataOfflinePipeline.AggregationState.SESSION_UNCERTAIN);
    }

    private static String row(String timestamp, String open, String high, String low, String close) {
        return timestamp + ";" + open + ";" + high + ";" + low + ";" + close + ";0";
    }
}
