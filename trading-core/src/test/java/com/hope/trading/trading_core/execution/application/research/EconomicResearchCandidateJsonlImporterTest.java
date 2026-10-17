package com.hope.trading.trading_core.execution.application.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.execution.domain.economic.CostCompleteness;
import com.hope.trading.trading_core.execution.domain.economic.EconomicResult;
import com.hope.trading.trading_core.execution.domain.economic.EconomicResultStatus;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecutionStatus;
import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;
import com.hope.trading.trading_core.execution.domain.economic.research.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

class EconomicResearchCandidateJsonlImporterTest {
    private static final UUID MARKET = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_MARKET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant T0 = Instant.parse("2026-10-17T10:00:00Z");

    @Test
    void roundTripsCanonicalCandidateAndRejectsUnknownFields() {
        EconomicResearchCandidate candidate = candidate();
        var serializer = new EconomicResearchCanonicalSerializer(new ObjectMapper().findAndRegisterModules());
        var importer = new EconomicResearchCandidateJsonlImporter(new ObjectMapper().findAndRegisterModules());

        assertThat(importer.importLines(serializer.candidate(candidate))).containsExactly(candidate);
        assertThatThrownBy(() -> importer.importLines(serializer.candidate(candidate).replaceFirst("\\{", "{\"unknown\":1,")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown candidate field");
    }

    @Test
    void rejectsFingerprintMismatchDuplicateAndLimitOrder() {
        EconomicResearchCandidate candidate = candidate();
        var serializer = new EconomicResearchCanonicalSerializer(new ObjectMapper().findAndRegisterModules());
        var line = serializer.candidate(candidate);
        var importer = new EconomicResearchCandidateJsonlImporter(new ObjectMapper().findAndRegisterModules(), 1);

        assertThatThrownBy(() -> importer.importLines(line.replace(candidate.fingerprint(), "0".repeat(64))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("candidateFingerprint");
        assertThatThrownBy(() -> importer.importLines(line + "\n" + line))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bound");
        assertThatThrownBy(() -> importer.importLines(line.replace("\"MARKET\"", "\"LIMIT\"")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIntentDecisionTimeThatDiffersFromSnapshotCutoff() {
        assertThatThrownBy(() -> candidateWith(MARKET, T0, T0.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decisionAt");
    }

    @Test
    void exportIdentityAndCanonicalOutputDoNotDependOnInputOrder() {
        EconomicResearchCandidate first = candidate();
        EconomicResearchCandidate second = candidateWith(SECOND_MARKET, T0.plusSeconds(1), T0.plusSeconds(1));
        var serializer = new EconomicResearchCanonicalSerializer(new ObjectMapper().findAndRegisterModules());
        var firstResult = unavailable(first);
        var secondResult = unavailable(second);
        var export = new EconomicResearchExportService(serializer);

        var forward = export.render(List.of(first, second), List.of(firstResult, secondResult));
        var reverse = export.render(List.of(second, first), List.of(secondResult, firstResult));

        assertThat(reverse.candidateJsonl()).isEqualTo(forward.candidateJsonl());
        assertThat(reverse.resultJsonl()).isEqualTo(forward.resultJsonl());
        assertThat(reverse.manifest().fingerprint()).isEqualTo(forward.manifest().fingerprint());
        assertThat(reverse.manifest().sourceBundleFingerprint())
                .isEqualTo(forward.manifest().sourceBundleFingerprint());
    }

    @Test
    void rejectsRequestCostModelThatDiffersFromCandidate() {
        EconomicResearchCandidate candidate = candidate();
        var serializer = new EconomicResearchCanonicalSerializer(new ObjectMapper().findAndRegisterModules());
        var controller = new EconomicResearchEvaluationController(new ObjectMapper().findAndRegisterModules(),
                org.mockito.Mockito.mock(HistoricalEconomicResearchEvaluationService.class));

        assertThatThrownBy(() -> controller.evaluate(new EconomicResearchEvaluationController.EvaluationRequest(
                serializer.candidate(candidate), T0.plus(Duration.ofHours(1)).toString(), "other-cost-v1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transactionCostModelVersion");
    }

    @Test
    void rejectsUnknownNestedFields() {
        EconomicResearchCandidate candidate = candidate();
        var serializer = new EconomicResearchCanonicalSerializer(new ObjectMapper().findAndRegisterModules());
        String line = serializer.candidate(candidate).replace("\"intent\":{\"instrument\"",
                "\"intent\":{\"unknown\":true,\"instrument\"");

        assertThatThrownBy(() -> new EconomicResearchCandidateJsonlImporter(new ObjectMapper().findAndRegisterModules())
                .importLines(line))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown intent field");
    }

    @Test
    void deterministicFixtureRunsImportEvaluationAndExportTogether() {
        EconomicResearchCandidate candidate = candidate();
        var serializer = new EconomicResearchCanonicalSerializer(new ObjectMapper().findAndRegisterModules());
        var evaluation = org.mockito.Mockito.mock(HistoricalEconomicResearchEvaluationService.class);
        when(evaluation.evaluate(any(), any(), any())).thenReturn(unavailable(candidate));
        var controller = new EconomicResearchEvaluationController(new ObjectMapper().findAndRegisterModules(), evaluation);

        var response = controller.evaluate(new EconomicResearchEvaluationController.EvaluationRequest(
                serializer.candidate(candidate), T0.plus(Duration.ofHours(1)).toString(), "cost-v1")).getBody();

        assertThat(response).isNotNull();
        assertThat(response.candidateJsonl()).contains(candidate.fingerprint());
        assertThat(response.resultJsonl()).contains("EXECUTION_UNAVAILABLE");
        assertThat(response.manifestJson()).contains("\"candidateCount\":1", "\"resultCount\":1");
        assertThat(response.manifestFingerprint()).isNotBlank();
    }

    @Test
    void exportsReconciledFilesWithByteChecksum(@TempDir Path directory) throws Exception {
        directory = directory.resolve("bundle");
        EconomicResearchCandidate candidate = candidate();
        var result = unavailable(candidate);
        var export = new EconomicResearchExportService(new EconomicResearchCanonicalSerializer(
                new ObjectMapper().findAndRegisterModules()));

        var manifest = export.export(List.of(candidate), List.of(result), directory);
        String candidates = java.nio.file.Files.readString(directory.resolve(EconomicResearchExportService.CANDIDATES_FILE));
        String results = java.nio.file.Files.readString(directory.resolve(EconomicResearchExportService.RESULTS_FILE));
        assertThat(manifest.candidateCount()).isEqualTo(1);
        assertThat(manifest.resultCount()).isEqualTo(1);
        assertThat(manifest.candidatesSha256()).isEqualTo(sha256(candidates));
        assertThat(manifest.resultsSha256()).isEqualTo(sha256(results));
        assertThat(java.nio.file.Files.exists(directory.resolve(EconomicResearchExportService.MANIFEST_FILE))).isTrue();
    }

    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private EconomicResearchResult unavailable(EconomicResearchCandidate candidate) {
        return new EconomicResearchResult(candidate.fingerprint(), null,
                EconomicResult.unavailable(EconomicResultStatus.EXECUTION_UNAVAILABLE, "no evidence"),
                EconomicResearchResultDisposition.EXECUTION_UNAVAILABLE, "no evidence", null);
    }

    private EconomicResearchCandidate candidate() {
        return candidateWith(MARKET, T0, T0);
    }

    private EconomicResearchCandidate candidateWith(UUID market, Instant snapshotCutoff, Instant decisionAt) {
        var hypothesis = new StrategyHypothesisIdentity("always-long", "v1");
        var sizing = new FixedBaseQuantityPolicy("size-v1", BigDecimal.ONE);
        var exit = new TimeHorizonExitPolicy("exit-v1", Duration.ofHours(1));
        var definition = new EconomicResearchDatasetDefinition("economic-v1", "population-v1",
                List.of(MARKET, SECOND_MARKET), T0.minusSeconds(1), T0.plus(Duration.ofHours(2)),
                "feature-v1", "evidence-v1", hypothesis,
                sizing, "entry-v1", exit, "execution-v1", "cost-v1", "cutoff-v1", CostCompleteness.FULLY_COSTED,
                Set.of(SimulatedExecutionStatus.FILLED), null);
        return new EconomicResearchCandidate(definition,
                new FeatureSnapshotReference(market, snapshotCutoff, "feature-v1", "context-" + market,
                        "snapshot-" + market, "identity-" + market), hypothesis,
                new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.MARKET,
                        BigDecimal.ONE, decisionAt),
                sizing, exit, "execution-v1", "cost-v1");
    }
}
