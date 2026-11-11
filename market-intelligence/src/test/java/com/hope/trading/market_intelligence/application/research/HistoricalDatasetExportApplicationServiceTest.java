package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hope.trading.market_intelligence.adapter.tradingcore.ResearchAuthorizationClient;
import com.hope.trading.market_intelligence.application.research.histdata.HistoricalClosureEvidenceSource;
import com.hope.trading.market_intelligence.application.research.histdata.Ml01HistDataPreparation;
import com.hope.trading.market_intelligence.application.research.histdata.Ml01ExploratoryH1AdmissibilityProfile;
import com.hope.trading.market_intelligence.infrastructure.research.HistoricalDatasetExportJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class HistoricalDatasetExportApplicationServiceTest {
    private static final DateTimeFormatter SOURCE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss");
    private static final UUID EUR_USD = UUID.nameUUIDFromBytes("histdata:market:EUR/USD".getBytes());
    private static final Instant START = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-09-05T00:00:00Z");

    @TempDir
    Path temp;

    @Test
    void exportApplicationTransmitsScopedEvidenceAndKeepsReopeningMove(@TempDir Path input) throws Exception {
        writeSources(input, 96, 40);
        var evidence = evidence(Instant.parse("2026-09-02T05:00:00Z"), "closure-one");
        var source = (HistoricalClosureEvidenceSource) (markets, start, end) -> List.of(evidence);
        var service = service(input, source);

        var result = service.prepareMl01Export(spec(Set.of(EUR_USD)), temp.resolve("with-evidence"));
        String quality = Files.readString(result.outputDirectory().resolve("quality-report.json"));
        String rows = Files.readString(result.outputDirectory().resolve("observations.jsonl"));

        assertThat(result.eligibleRows()).isGreaterThan(0);
        assertThat(quality).contains("VERIFIED_CLOSURE");
        assertThat(rows).contains("CONTIGUOUS_ONLY");
        assertThat(rows).contains("\"absoluteFutureNetReturn\"");
    }

    @Test
    void missingEvidenceRemainsFailClosed(@TempDir Path input) throws Exception {
        writeSources(input, 96, 40);
        var service = service(input, (markets, start, end) -> List.of());

        var result = service.prepareMl01Export(spec(Set.of(EUR_USD)), temp.resolve("without-evidence"));

        assertThat(Files.readString(result.outputDirectory().resolve("observations.jsonl")))
                .contains("UNEXPLAINED_GAP");
        assertThat(result.eligibleRows()).isLessThan(result.rows());
    }

    @Test
    void contradictoryEvidenceRemainsFailClosed(@TempDir Path input) throws Exception {
        writeSources(input, 96, 40);
        var first = evidence(Instant.parse("2026-09-02T05:00:00Z"), "closure-one");
        var second = evidence(Instant.parse("2026-09-02T05:00:00Z"), "closure-two");
        var service = service(input, (markets, start, end) -> List.of(first, second));

        var result = service.prepareMl01Export(spec(Set.of(EUR_USD)), temp.resolve("ambiguous"));

        assertThat(Files.readString(result.outputDirectory().resolve("observations.jsonl")))
                .contains("UNEXPLAINED_GAP")
                .doesNotContain("VERIFIED_CLOSURE");
    }

    @Test
    void rejectsOutOfScopeAndLateEvidenceBeforePreparation(@TempDir Path input) throws Exception {
        writeSources(input, 96, 40);
        var wrongMarket = new HistoricalClosureEvidenceSource.Evidence(
                UUID.randomUUID(), UUID.randomUUID(), "EUR/USD", "H1", "HISTDATA", "HOLIDAY", "test",
                Instant.parse("2026-09-02T21:00:00Z"), Instant.parse("2026-09-02T22:00:00Z"),
                "calendar", "v1", Instant.parse("2026-09-01T00:00:00Z"), END,
                Instant.parse("2026-09-01T00:00:00Z"), "wrong-market");
        var service = service(input, (markets, start, end) -> List.of(wrongMarket));

        assertThatThrownBy(() -> service.prepareMl01Export(spec(Set.of(EUR_USD)), temp.resolve("wrong-market")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changingEvidenceFingerprintChangesDatasetFingerprint(@TempDir Path input) throws Exception {
        writeSources(input, 96, 40);
        var first = service(input, (markets, start, end) -> List.of(evidence(
                Instant.parse("2026-09-02T05:00:00Z"), "closure-one")));
        var second = service(input, (markets, start, end) -> List.of(evidence(
                Instant.parse("2026-09-02T05:00:00Z"), "closure-two")));

        var left = first.prepareMl01Export(spec(Set.of(EUR_USD)), temp.resolve("fingerprint-one"));
        var right = second.prepareMl01Export(spec(Set.of(EUR_USD)), temp.resolve("fingerprint-two"));

        assertThat(left.datasetFingerprint()).isNotEqualTo(right.datasetFingerprint());
    }

    @Test
    void evidenceKnownAfterTheTransitionCutoffIsNotAccepted(@TempDir Path input) throws Exception {
        writeSources(input, 96, 40);
        var service = service(input, (markets, start, end) -> List.of(evidence(
                Instant.parse("2026-09-02T21:30:00Z"), "late-evidence")));

        var result = service.prepareMl01Export(spec(Set.of(EUR_USD)), temp.resolve("late-evidence"));

        assertThat(Files.readString(result.outputDirectory().resolve("observations.jsonl")))
                .contains("UNEXPLAINED_GAP");
    }

    @Test
    void acceptsTheResearchOnlyMl01ExportContract() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode population = mapper.createObjectNode();
        population.put("datasetProfile", "ml01-h1-histdata-v1");
        ArrayNode markets = population.putArray("marketIds");
        markets.add(EUR_USD.toString());
        markets.add(UUID.nameUUIDFromBytes("histdata:market:GBP/USD".getBytes()).toString());
        population.put("timeframe", "H1");
        population.put("researchFeatureSchemaVersion", Ml01HistDataPreparation.RESEARCH_FEATURE_SCHEMA_VERSION);
        population.put("researchFeatureEvidencePolicy", Ml01HistDataPreparation.RESEARCH_EXPORT_POLICY);
        population.put("sourceProfile", Ml01HistDataPreparation.SOURCE_PROFILE);
        population.put("admissibilityProfile", Ml01ExploratoryH1AdmissibilityProfile.V1.name());
        population.put("admissibilityPolicyVersion", Ml01ExploratoryH1AdmissibilityProfile.V1.version());
        population.put("outcomeDefinitionVersion", Ml01HistDataPreparation.OUTCOME_DEFINITION_VERSION);
        population.put("continuityScope", Ml01HistDataPreparation.CONTINUITY_SCOPE);
        population.put("sessionTransitionStatus", Ml01HistDataPreparation.SESSION_TRANSITION_STATUS);
        population.put("sourceChecksumAlgorithm", Ml01HistDataPreparation.SOURCE_CHECKSUM_ALGORITHM);
        population.put("sourceChecksumScope", Ml01HistDataPreparation.SOURCE_CHECKSUM_SCOPE);
        population.put("normalizationVersion", Ml01HistDataPreparation.NORMALIZATION_VERSION);
        ArrayNode horizons = population.putArray("horizons");
        horizons.addObject().put("validH1Candles", 4);
        horizons.addObject().put("validH1Candles", 12);
        horizons.addObject().put("validH1Candles", 24);
        population.put("startCutoffInclusive", START.toString());
        population.put("endCutoffExclusive", END.toString());

        var parsed = service(temp, (marketsArg, start, end) -> List.of()).readMl01ExportSpec(population);

        assertThat(parsed.datasetProfile()).isEqualTo("ml01-h1-histdata-v1");
        assertThat(parsed.marketIds()).hasSize(2);

        population.remove("sourceChecksumAlgorithm");
        assertThatThrownBy(() -> service(temp, (marketsArg, start, end) -> List.of())
                .readMl01ExportSpec(population))
                .isInstanceOf(HistoricalResearchException.class);
    }

    private HistoricalDatasetExportApplicationService service(Path input,
                                                               HistoricalClosureEvidenceSource source) {
        return new HistoricalDatasetExportApplicationService(
                mock(HistoricalDatasetExportJobRepository.class), mock(ResearchAuthorizationClient.class),
                mock(ResearchDatasetExportService.class), new ObjectMapper(), Clock.systemUTC(),
                (Executor) Runnable::run, temp.resolve("exports").toString(), input.toString(), source);
    }

    private HistoricalDatasetExportApplicationService.Ml01ExportSpec spec(Set<UUID> markets) {
        return new HistoricalDatasetExportApplicationService.Ml01ExportSpec(markets, START, END,
                "ml01-h1-histdata-v1", new Ml01HistDataPreparation.PreparationScope(markets, START, END),
                Ml01HistDataPreparation.PartitionConfiguration.requiresApproval());
    }

    private HistoricalClosureEvidenceSource.Evidence evidence(Instant knownAt, String fingerprint) {
        return new HistoricalClosureEvidenceSource.Evidence(UUID.randomUUID(), EUR_USD, "EUR/USD", "H1",
                "HISTDATA", "HOLIDAY", "test closure", Instant.parse("2026-09-02T21:00:00Z"),
                Instant.parse("2026-09-02T22:00:00Z"), "calendar", "v1", START, END, knownAt, fingerprint);
    }

    private void writeSources(Path directory, int hours, int missingHour) throws Exception {
        for (String symbol : List.of("EURUSD", "GBPUSD")) {
            StringBuilder content = new StringBuilder();
            LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
            for (int minute = 0; minute < hours * 60; minute++) {
                if (minute >= missingHour * 60 && minute < (missingHour + 1) * 60) continue;
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
    }
}
