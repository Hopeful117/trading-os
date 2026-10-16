package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.feature.FeatureProjectionV1;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.feature.*;
import com.hope.trading.market_intelligence.domain.outcome.*;
import com.hope.trading.market_intelligence.domain.research.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResearchDatasetBuilderTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant T0 = Instant.parse("2026-10-15T10:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsOneDeterministicSamplePerSnapshotAndHorizon() {
        FeatureSnapshot snapshot = snapshot("snapshot-v1");
        ObservedMarketOutcome oneHour = outcome(OutcomeHorizon.oneHour(), "outcome-1h", ObservedMarketOutcomeStatus.AVAILABLE);
        ObservedMarketOutcome fourHours = outcome(OutcomeHorizon.fourHours(), "outcome-4h", ObservedMarketOutcomeStatus.AVAILABLE);
        ObservedMarketOutcome oneDay = outcome(OutcomeHorizon.oneDay(), "outcome-24h", ObservedMarketOutcomeStatus.AVAILABLE);
        ResearchPopulationSpec population = population(Map.of(
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour()), "outcome-1h",
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.fourHours()), "outcome-4h",
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneDay()), "outcome-24h"),
                Set.of(OutcomeHorizon.oneHour(), OutcomeHorizon.fourHours(), OutcomeHorizon.oneDay()));

        DatasetBuildResult result = builder().build(population, List.of(snapshot), List.of(oneDay, oneHour, fourHours));

        assertThat(result.manifest().eligibleCount()).isEqualTo(3);
        assertThat(result.samples()).extracting(ResearchSample::horizon)
                .containsExactly(OutcomeHorizon.oneHour(), OutcomeHorizon.fourHours(), OutcomeHorizon.oneDay());
    }

    @Test
    void preservesMissingnessAndTypesInJsonlExport() throws Exception {
        FeatureSnapshot snapshot = snapshot("snapshot-export");
        ObservedMarketOutcome outcome = outcome(OutcomeHorizon.oneHour(), "outcome-export", ObservedMarketOutcomeStatus.AVAILABLE);
        ResearchPopulationSpec population = population(Map.of(
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour()), "outcome-export"),
                Set.of(OutcomeHorizon.oneHour()));

        DatasetBuildResult result = builder().build(population, List.of(snapshot), List.of(outcome));
        String json = new ResearchDatasetJsonlExporter(new ObjectMapper()).export(result);
        var document = new ObjectMapper().readTree(json);

        assertThat(document.get("status").asText()).isEqualTo("ELIGIBLE");
        assertThat(document.get("features").get("ohlc.range_fraction").get("type").asText()).isEqualTo("NUMERIC");
        assertThat(document.get("features").get("trend.bias.direction").get("type").asText()).isEqualTo("CATEGORICAL");
        assertThat(document.get("features").get("trend.bias.pullback_qualified").get("type").asText()).isEqualTo("BOOLEAN");
        assertThat(document.get("features").get("trend.trigger.atr_ratio").get("status").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(document.get("features").get("trend.trigger.atr_ratio").get("value").isNull()).isTrue();
        assertThat(document.get("outcome").get("forwardReturn").decimalValue()).isEqualByComparingTo("0.02");
    }

    @Test
    void excludesUnselectedOrIncompleteOutcomeWithoutInventingValues() {
        FeatureSnapshot snapshot = snapshot("snapshot-exclusion");
        ObservedMarketOutcome incomplete = outcome(OutcomeHorizon.oneHour(), "outcome-incomplete", ObservedMarketOutcomeStatus.INCOMPLETE);
        ResearchPopulationSpec population = population(Map.of(
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour()), "outcome-incomplete"),
                Set.of(OutcomeHorizon.oneHour()));

        DatasetBuildResult incompleteResult = builder().build(population, List.of(snapshot), List.of(incomplete));
        DatasetBuildResult unselectedResult = builder().build(population(
                Map.of(), Set.of(OutcomeHorizon.oneHour())), List.of(snapshot), List.of(incomplete));

        assertThat(incompleteResult.samples().getFirst().exclusionReason()).isEqualTo(ResearchExclusionReason.OUTCOME_INCOMPLETE);
        assertThat(unselectedResult.samples().getFirst().exclusionReason()).isEqualTo(ResearchExclusionReason.OUTCOME_VERSION_NOT_SELECTED);
        assertThat(incompleteResult.samples().getFirst().outcome().forwardReturn()).isNull();
    }

    @Test
    void samePopulationAndReorderedInputsProduceSameDatasetFingerprint() {
        FeatureSnapshot snapshot = snapshot("snapshot-stable");
        ObservedMarketOutcome first = outcome(OutcomeHorizon.oneHour(), "outcome-stable-1", ObservedMarketOutcomeStatus.AVAILABLE);
        ObservedMarketOutcome second = outcome(OutcomeHorizon.fourHours(), "outcome-stable-4", ObservedMarketOutcomeStatus.AVAILABLE);
        ResearchPopulationSpec population = population(Map.of(
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour()), "outcome-stable-1",
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.fourHours()), "outcome-stable-4"),
                Set.of(OutcomeHorizon.oneHour(), OutcomeHorizon.fourHours()));

        DatasetBuildResult left = builder().build(population, List.of(snapshot), List.of(first, second));
        DatasetBuildResult right = builder().build(population, List.of(snapshot), List.of(second, first));

        assertThat(right.manifest().datasetFingerprint()).isEqualTo(left.manifest().datasetFingerprint());
        assertThat(right.samples()).extracting(ResearchSample::sampleFingerprint)
                .containsExactlyElementsOf(left.samples().stream().map(ResearchSample::sampleFingerprint).toList());
    }

    @Test
    void exportsAtomicBundleWithManifestAndSampleChecksum() throws Exception {
        FeatureSnapshot snapshot = snapshot("snapshot-bundle");
        ObservedMarketOutcome outcome = outcome(OutcomeHorizon.oneHour(), "outcome-bundle", ObservedMarketOutcomeStatus.AVAILABLE);
        ResearchPopulationSpec population = population(Map.of(
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour()), "outcome-bundle"),
                Set.of(OutcomeHorizon.oneHour()));
        DatasetBuildResult result = builder().build(population, List.of(snapshot), List.of(outcome));
        Path output = temporaryDirectory.resolve("bundle");

        new ResearchDatasetBundleExporter(new ObjectMapper()).export(result, output);

        assertThat(Files.exists(output.resolve(ResearchDatasetBundleExporter.MANIFEST_FILE))).isTrue();
        assertThat(Files.exists(output.resolve(ResearchDatasetBundleExporter.SAMPLES_FILE))).isTrue();
        assertThat(Files.exists(output.resolve(ResearchDatasetBundleExporter.README_FILE))).isTrue();
        String samples = Files.readString(output.resolve(ResearchDatasetBundleExporter.SAMPLES_FILE));
        var manifest = new ObjectMapper().readTree(Files.readString(output.resolve(ResearchDatasetBundleExporter.MANIFEST_FILE)));
        assertThat(manifest.get("datasetFingerprint").asText()).isEqualTo(result.manifest().datasetFingerprint());
        assertThat(manifest.get("fingerprintMaterial")).hasSize(result.manifest().fingerprintMaterial().size());
        assertThat(manifest.get("samplesSha256").asText()).isEqualTo(
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(samples.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    @Test
    void repeatedExportsProduceIdenticalBundleBytesAndZeroSamplesAreRepresentable() throws Exception {
        ResearchPopulationSpec population = population(Map.of(), Set.of(OutcomeHorizon.oneHour()));
        DatasetBuildResult result = builder().build(population, List.of(), List.of());
        Path first = temporaryDirectory.resolve("first");
        Path second = temporaryDirectory.resolve("second");
        ResearchDatasetBundleExporter exporter = new ResearchDatasetBundleExporter(new ObjectMapper());

        exporter.export(result, first);
        exporter.export(result, second);

        assertThat(Files.readAllBytes(first.resolve(ResearchDatasetBundleExporter.MANIFEST_FILE)))
                .isEqualTo(Files.readAllBytes(second.resolve(ResearchDatasetBundleExporter.MANIFEST_FILE)));
        assertThat(Files.readAllBytes(first.resolve(ResearchDatasetBundleExporter.SAMPLES_FILE)))
                .isEqualTo(Files.readAllBytes(second.resolve(ResearchDatasetBundleExporter.SAMPLES_FILE)));
        assertThat(Files.size(first.resolve(ResearchDatasetBundleExporter.SAMPLES_FILE))).isZero();
    }

    @Test
    void currentExporterMatchesCommittedCrossLanguageFixture() throws Exception {
        FeatureSnapshot snapshot = snapshot("snapshot-cross-language");
        ObservedMarketOutcome outcome = outcome(OutcomeHorizon.oneHour(), "outcome-cross-language", ObservedMarketOutcomeStatus.AVAILABLE);
        ResearchPopulationSpec population = population(Map.of(
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour()), "outcome-cross-language"),
                Set.of(OutcomeHorizon.oneHour()));
        DatasetBuildResult result = builder().build(population, List.of(snapshot), List.of(outcome));
        Path fixture = Path.of(System.getProperty("project.basedir", "."))
                .resolve("../research/ml/tests/fixtures/java-generated").normalize();
        Path generated = temporaryDirectory.resolve("cross-language");

        new ResearchDatasetBundleExporter(new ObjectMapper()).export(result, generated);

        assertThat(Files.readAllBytes(generated.resolve(ResearchDatasetBundleExporter.MANIFEST_FILE)))
                .isEqualTo(Files.readAllBytes(fixture.resolve(ResearchDatasetBundleExporter.MANIFEST_FILE)));
        assertThat(Files.readAllBytes(generated.resolve(ResearchDatasetBundleExporter.SAMPLES_FILE)))
                .isEqualTo(Files.readAllBytes(fixture.resolve(ResearchDatasetBundleExporter.SAMPLES_FILE)));
    }

    @Test
    void datasetManifestValidatesCountsAndFingerprintMaterial() {
        List<String> material = List.of("material");
        assertThatThrownBy(() -> manifest(-1, 0, 0, material))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid dataset counts");
        assertThatThrownBy(() -> manifest(1, 0, 0, material))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid dataset counts");
        assertThatThrownBy(() -> manifest(1, 1, 1, material))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid dataset counts");
        assertThatThrownBy(() -> manifest(0, 0, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fingerprint material must not be empty");
    }

    @Test
    void datasetManifestExposesConfiguredHorizons() {
        DatasetManifest manifest = manifest(0, 0, 0, List.of("material"));

        assertThat(manifest.horizons()).containsExactly(OutcomeHorizon.oneHour());
    }

    @Test
    void correctedOutcomeChangesSampleAndDatasetFingerprintWithoutChangingFeatureSnapshot() {
        FeatureSnapshot snapshot = snapshot("snapshot-correction");
        ObservedMarketOutcome original = outcome(OutcomeHorizon.oneHour(), "outcome-original", ObservedMarketOutcomeStatus.AVAILABLE);
        ObservedMarketOutcome corrected = outcome(OutcomeHorizon.oneHour(), "outcome-corrected", ObservedMarketOutcomeStatus.AVAILABLE);
        ResearchOutcomeKey key = new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour());

        DatasetBuildResult originalDataset = builder().build(population(Map.of(key, "outcome-original"), Set.of(OutcomeHorizon.oneHour())),
                List.of(snapshot), List.of(original, corrected));
        DatasetBuildResult correctedDataset = builder().build(population(Map.of(key, "outcome-corrected"), Set.of(OutcomeHorizon.oneHour())),
                List.of(snapshot), List.of(original, corrected));

        assertThat(correctedDataset.samples().getFirst().sampleFingerprint())
                .isNotEqualTo(originalDataset.samples().getFirst().sampleFingerprint());
        assertThat(correctedDataset.manifest().datasetFingerprint())
                .isNotEqualTo(originalDataset.manifest().datasetFingerprint());
        assertThat(snapshot.fingerprint()).isEqualTo("snapshot-correction");
    }

    @Test
    void excludesDuplicateSnapshotAndRejectsConflictingDuplicateSnapshot() {
        FeatureSnapshot snapshot = snapshot("snapshot-duplicate");
        ObservedMarketOutcome outcome = outcome(OutcomeHorizon.oneHour(), "outcome-duplicate", ObservedMarketOutcomeStatus.AVAILABLE);
        ResearchPopulationSpec population = population(Map.of(
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour()), "outcome-duplicate"),
                Set.of(OutcomeHorizon.oneHour()));

        DatasetBuildResult result = builder().build(population, List.of(snapshot, snapshot), List.of(outcome));

        assertThat(result.manifest().eligibleCount()).isEqualTo(1);
        assertThat(result.manifest().excludedCount()).isEqualTo(1);
        assertThat(result.samples()).extracting(ResearchSample::exclusionReason)
                .containsExactly(null, ResearchExclusionReason.DUPLICATE_FEATURE_SNAPSHOT);
    }

    @Test
    void distinguishesMissingSelectedOutcomeFromWrongOutcomeVersion() {
        FeatureSnapshot snapshot = snapshot("snapshot-missing-outcome");
        ResearchOutcomeKey key = new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour());
        ResearchPopulationSpec population = population(Map.of(key, "selected"), Set.of(OutcomeHorizon.oneHour()));

        DatasetBuildResult missing = builder().build(population, List.of(snapshot), List.of());
        DatasetBuildResult wrongFingerprint = builder().build(population, List.of(snapshot),
                List.of(outcome(OutcomeHorizon.oneHour(), "other", ObservedMarketOutcomeStatus.AVAILABLE)));

        assertThat(missing.samples().getFirst().exclusionReason()).isEqualTo(ResearchExclusionReason.OUTCOME_MISSING);
        assertThat(wrongFingerprint.samples().getFirst().exclusionReason()).isEqualTo(ResearchExclusionReason.OUTCOME_VERSION_MISMATCH);
    }

    @Test
    void excludesSnapshotThatDoesNotConformToDeclaredSchema() {
        FeatureSnapshot invalid = new FeatureSnapshot(UUID.randomUUID(), UUID.randomUUID(), MARKET_ID, T0,
                EvidenceAcquisitionPolicy.STRICT_OBSERVED, "feature-projection-v1", "invalid-context",
                List.of(new NumericFeatureValue("unknown", FeatureMissingness.PRESENT, BigDecimal.ONE)),
                List.of(), List.of(), "invalid-snapshot", T0.plusSeconds(1));
        ResearchOutcomeKey key = new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour());
        ResearchPopulationSpec population = population(Map.of(key, "outcome-invalid"), Set.of(OutcomeHorizon.oneHour()));

        DatasetBuildResult result = builder().build(population, List.of(invalid),
                List.of(outcome(OutcomeHorizon.oneHour(), "outcome-invalid", ObservedMarketOutcomeStatus.AVAILABLE)));

        assertThat(result.samples().getFirst().exclusionReason()).isEqualTo(ResearchExclusionReason.FEATURE_SNAPSHOT_INVALID);
    }

    @Test
    void rejectsConflictingOutcomesWithTheSameSelectedIdentity() {
        FeatureSnapshot snapshot = snapshot("snapshot-outcome-conflict");
        ResearchOutcomeKey key = new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour());
        ResearchPopulationSpec population = population(Map.of(key, "outcome-conflict"), Set.of(OutcomeHorizon.oneHour()));
        ObservedMarketOutcome first = outcomeWithFinalPrice("outcome-conflict", new BigDecimal("102"));
        ObservedMarketOutcome second = outcomeWithFinalPrice("outcome-conflict", new BigDecimal("103"));

        ResearchDatasetBuilder builder = builder();
        Throwable conflict = catchThrowable(() -> builder.build(population, List.of(snapshot), List.of(first, second)));
        assertThat(conflict)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting outcomes");
    }

    @Test
    void rejectsUnsupportedFeatureProjectionVersion() {
        FeatureSnapshot snapshot = snapshot("snapshot-unsupported-version");
        ResearchPopulationSpec population = new ResearchPopulationSpec("population-v1", Set.of(), T0.minusSeconds(1),
                T0.plusSeconds(1), "feature-projection-v2", EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                "market-outcome-v1", Set.of(OutcomeHorizon.oneHour()), ResearchSamplingCadence.ONE_PER_FEATURE_SNAPSHOT,
                Set.of(FeatureMissingness.INSUFFICIENT_DATA), Map.of());

        ResearchDatasetBuilder builder = builder();
        Throwable unsupported = catchThrowable(() -> builder.build(population, List.of(snapshot), List.of()));
        assertThat(unsupported)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported feature projection version");
    }

    @Test
    void excludesSnapshotWithMismatchedProjectionVersion() {
        FeatureSnapshot base = snapshot("snapshot-version-mismatch");
        FeatureSnapshot mismatched = new FeatureSnapshot(base.snapshotId(), base.analysisExecutionId(), base.marketId(),
                base.evaluationCutoff(), base.evidencePolicy(), "feature-projection-v2", base.contextFingerprint(),
                base.values(), base.groups(), base.provenance(), base.fingerprint(), base.createdAt());
        ResearchPopulationSpec population = population(Map.of(
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour()), "outcome-version-mismatch"),
                Set.of(OutcomeHorizon.oneHour()));

        DatasetBuildResult result = builder().build(population, List.of(mismatched),
                List.of(outcome(OutcomeHorizon.oneHour(), "outcome-version-mismatch", ObservedMarketOutcomeStatus.AVAILABLE)));

        assertThat(result.samples().getFirst().exclusionReason()).isEqualTo(ResearchExclusionReason.VERSION_MISMATCH);
    }

    @Test
    void excludesSnapshotWithFeatureMissingnessThatPopulationDoesNotAccept() {
        FeatureSnapshot snapshot = snapshot("snapshot-required-feature");
        ResearchPopulationSpec population = new ResearchPopulationSpec("population-v1", Set.of(), T0.minusSeconds(1),
                T0.plusSeconds(1), "feature-projection-v1", EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                "market-outcome-v1", Set.of(OutcomeHorizon.oneHour()), ResearchSamplingCadence.ONE_PER_FEATURE_SNAPSHOT,
                Set.of(), Map.of(new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour()), "outcome-required-feature"));

        DatasetBuildResult result = builder().build(population, List.of(snapshot),
                List.of(outcome(OutcomeHorizon.oneHour(), "outcome-required-feature", ObservedMarketOutcomeStatus.AVAILABLE)));

        assertThat(result.samples().getFirst().exclusionReason())
                .isEqualTo(ResearchExclusionReason.FEATURE_MISSING_REQUIRED_VALUE);
    }

    @Test
    void excludesPendingAndUnavailableOutcomesWithoutTreatingThemAsEligible() {
        FeatureSnapshot snapshot = snapshot("snapshot-outcome-status");
        ResearchPopulationSpec pendingPopulation = population(Map.of(
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour()), "outcome-pending"),
                Set.of(OutcomeHorizon.oneHour()));
        ResearchPopulationSpec unavailablePopulation = population(Map.of(
                new ResearchOutcomeKey(MARKET_ID, T0, OutcomeHorizon.oneHour()), "outcome-unavailable"),
                Set.of(OutcomeHorizon.oneHour()));

        DatasetBuildResult pending = builder().build(pendingPopulation, List.of(snapshot),
                List.of(outcome(OutcomeHorizon.oneHour(), "outcome-pending", ObservedMarketOutcomeStatus.PENDING)));
        DatasetBuildResult unavailable = builder().build(unavailablePopulation, List.of(snapshot),
                List.of(outcome(OutcomeHorizon.oneHour(), "outcome-unavailable", ObservedMarketOutcomeStatus.UNAVAILABLE)));

        assertThat(pending.samples().getFirst().exclusionReason()).isEqualTo(ResearchExclusionReason.OUTCOME_NOT_MATURE);
        assertThat(unavailable.samples().getFirst().exclusionReason()).isEqualTo(ResearchExclusionReason.OUTCOME_UNAVAILABLE);
    }

    @Test
    void validatesPopulationTemporalAndEvidenceConstraints() {
        assertThatThrownBy(this::populationWithInvalidInterval)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("start cutoff must precede end cutoff");
        assertThatThrownBy(this::populationWithLiveEvidence)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("research features require STRICT_OBSERVED evidence");
        assertThatThrownBy(this::populationWithoutHorizons)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("at least one horizon is required");
        assertThat(population(Map.of(), Set.of(OutcomeHorizon.oneHour())).interval())
                .isEqualTo(ResearchPopulationSpec.FUTURE_INTERVAL);
    }

    private ResearchDatasetBuilder builder() {
        return new ResearchDatasetBuilder(Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC));
    }

    private ResearchPopulationSpec populationWithInvalidInterval() {
        return new ResearchPopulationSpec("population-v1", Set.of(), T0, T0,
                "feature-projection-v1", EvidenceAcquisitionPolicy.STRICT_OBSERVED, "market-outcome-v1",
                Set.of(OutcomeHorizon.oneHour()), ResearchSamplingCadence.ONE_PER_FEATURE_SNAPSHOT,
                Set.of(FeatureMissingness.INSUFFICIENT_DATA), Map.of());
    }

    private ResearchPopulationSpec populationWithLiveEvidence() {
        return new ResearchPopulationSpec("population-v1", Set.of(), T0.minusSeconds(1), T0.plusSeconds(1),
                "feature-projection-v1", EvidenceAcquisitionPolicy.LIVE_CURRENT, "market-outcome-v1",
                Set.of(OutcomeHorizon.oneHour()), ResearchSamplingCadence.ONE_PER_FEATURE_SNAPSHOT,
                Set.of(FeatureMissingness.INSUFFICIENT_DATA), Map.of());
    }

    private ResearchPopulationSpec populationWithoutHorizons() {
        return new ResearchPopulationSpec("population-v1", Set.of(), T0.minusSeconds(1), T0.plusSeconds(1),
                "feature-projection-v1", EvidenceAcquisitionPolicy.STRICT_OBSERVED, "market-outcome-v1",
                Set.of(), ResearchSamplingCadence.ONE_PER_FEATURE_SNAPSHOT,
                Set.of(FeatureMissingness.INSUFFICIENT_DATA), Map.of());
    }

    private DatasetManifest manifest(int samples, int eligible, int excluded, List<String> material) {
        return new DatasetManifest("dataset-v1", population(Map.of(), Set.of(OutcomeHorizon.oneHour())),
                FeatureProjectionV1.schema(), List.of("forwardReturn"), samples, eligible, excluded,
                Map.of(), T0, "dataset-fingerprint", material);
    }

    private ResearchPopulationSpec population(Map<ResearchOutcomeKey, String> selected, Set<OutcomeHorizon> horizons) {
        return new ResearchPopulationSpec("population-v1", Set.of(), T0.minusSeconds(1), T0.plusSeconds(1),
                "feature-projection-v1", EvidenceAcquisitionPolicy.STRICT_OBSERVED, "market-outcome-v1",
                horizons, ResearchSamplingCadence.ONE_PER_FEATURE_SNAPSHOT,
                Set.of(FeatureMissingness.INSUFFICIENT_DATA), selected);
    }

    private FeatureSnapshot snapshot(String fingerprint) {
        List<FeatureValue> values = FeatureProjectionV1.schema().definitions().stream()
                .map(definition -> featureValue(definition, definition.name().equals("trend.trigger.atr_ratio")))
                .toList();
        return new FeatureSnapshot(UUID.randomUUID(), UUID.randomUUID(), MARKET_ID, T0,
                EvidenceAcquisitionPolicy.STRICT_OBSERVED, "feature-projection-v1", "context-" + fingerprint,
                values,
                List.of(), List.of(), fingerprint, T0.plusSeconds(1));
    }

    private FeatureValue featureValue(FeatureDefinition definition, boolean missing) {
        FeatureMissingness status = missing ? FeatureMissingness.INSUFFICIENT_DATA : FeatureMissingness.PRESENT;
        if (missing) {
            return switch (definition.dataType()) {
                case NUMERIC -> new NumericFeatureValue(definition.name(), status, null);
                case CATEGORICAL -> new CategoricalFeatureValue(definition.name(), status, null);
                case BOOLEAN -> new BooleanFeatureValue(definition.name(), status, null);
            };
        }
        return switch (definition.dataType()) {
            case NUMERIC -> new NumericFeatureValue(definition.name(), status, new BigDecimal("0.02"));
            case CATEGORICAL -> new CategoricalFeatureValue(definition.name(), status, "UPTREND");
            case BOOLEAN -> new BooleanFeatureValue(definition.name(), status, true);
        };
    }

    private ObservedMarketOutcome outcome(OutcomeHorizon horizon, String fingerprint, ObservedMarketOutcomeStatus status) {
        boolean available = status == ObservedMarketOutcomeStatus.AVAILABLE;
        return new ObservedMarketOutcome(MARKET_ID, T0, horizon, T0.plus(horizon.value()), status,
                available ? null : "INCOMPLETE", new BigDecimal("100"), "reference", available ? new BigDecimal("102") : null,
                available ? new BigDecimal("105") : null, available ? new BigDecimal("98") : null,
                available ? new BigDecimal("0.02") : null, available ? new BigDecimal("0.05") : null,
                available ? new BigDecimal("-0.02") : null, available ? new BigDecimal("0.07") : null,
                4, status == ObservedMarketOutcomeStatus.INCOMPLETE ? 3 : 4, "ohlc-v1", List.of("ohlc-v1"),
                 "future-" + fingerprint, "market-outcome-v1", fingerprint,
                 status == ObservedMarketOutcomeStatus.PENDING ? T0 : T0.plus(horizon.value()), null);
    }

    private ObservedMarketOutcome outcomeWithFinalPrice(String fingerprint, BigDecimal finalPrice) {
        ObservedMarketOutcome base = outcome(OutcomeHorizon.oneHour(), fingerprint, ObservedMarketOutcomeStatus.AVAILABLE);
        return new ObservedMarketOutcome(base.marketId(), base.evaluationCutoff(), base.horizon(), base.maturityAt(), base.status(),
                base.reason(), base.referencePrice(), base.referenceObservationFingerprint(), finalPrice, base.futureHigh(),
                base.futureLow(), base.forwardReturn(), base.futureHighReturn(), base.futureLowReturn(), base.futureRange(),
                base.expectedObservationCount(), base.observedObservationCount(), base.referenceNormalizationVersion(),
                base.futureNormalizationVersions(), base.futureEvidenceFingerprint(), base.outcomeDefinitionVersion(),
                base.outcomeFingerprint(), base.computedAt(), base.analysisExecutionId());
    }
}
