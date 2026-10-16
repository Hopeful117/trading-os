package com.hope.trading.market_intelligence.application.feature;

import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.ProducedArtifact;
import com.hope.trading.market_intelligence.domain.feature.*;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureArtifactContent;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureAvailability;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureResult;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureProjectionV1Test {
    private static final UUID MARKET_ID = TrendContextTestFixtures.MARKET_ID;
    private static final UUID ANALYSIS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant CUTOFF = Instant.parse("2026-09-24T12:00:00Z");

    @Test
    void schemaAndProjectionHaveStableTypedOrderAndSemantics() {
        FeatureProjectionV1.ProjectionResult first = projection();
        FeatureProjectionV1.ProjectionResult second = projection();

        assertThat(first.schema().version()).isEqualTo(FeatureProjectionV1.VERSION);
        assertThat(first.schema().definitions()).extracting(FeatureDefinition::order)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, first.values().size()).boxed().toList());
        assertThat(first.values()).extracting(FeatureValue::name)
                .containsExactlyElementsOf(first.schema().definitions().stream().map(FeatureDefinition::name).toList());
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
        assertThat(first.values()).filteredOn(value -> value.name().equals("ohlc.range_fraction"))
                .first().isInstanceOfSatisfying(NumericFeatureValue.class,
                        value -> assertThat(value.value()).isEqualByComparingTo("0.125"));
        assertThat(first.values()).filteredOn(value -> value.name().equals("ohlc.simple_return"))
                .first().isInstanceOfSatisfying(NumericFeatureValue.class,
                        value -> assertThat(value.value()).isEqualByComparingTo("0.025"));
        assertThat(first.values()).filteredOn(value -> value.name().equals("trend.bias.direction"))
                .first().extracting(FeatureValue::status).isEqualTo(FeatureMissingness.PRESENT);
        assertThat(first.values()).filteredOn(value -> value.name().equals("trend.trigger.direction"))
                .first().extracting(FeatureValue::status).isEqualTo(FeatureMissingness.UNAVAILABLE);
        assertThat(first.provenance()).extracting(AnalyzerProvenance::sourceArtifactContractVersion)
                .contains("1.0.0", "2.0.0");
        assertThat(first.provenance()).filteredOn(value ->
                        value.sourceArtifactType().equals(ProductionArtifactTypes.OHLC_RANGE_ANALYSIS.value()))
                .first().extracting(AnalyzerProvenance::evidenceFingerprint)
                .isEqualTo("ohlc-range-analysis-result-evidence");
        assertThat(first.provenance()).filteredOn(value ->
                        value.sourceArtifactType().equals(ProductionArtifactTypes.MARKET_STRUCTURE.value()))
                .first().extracting(AnalyzerProvenance::evidenceFingerprint)
                .isEqualTo("market-structure-evidence");
        assertThat(first.provenance()).filteredOn(value ->
                        value.sourceArtifactType().equals(ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT.value()))
                .first().extracting(AnalyzerProvenance::evidenceFingerprint)
                .isEqualTo("trend-context-assessment-evidence");
    }

    @Test
    void unavailableAnalyzerFieldIsNullAndNeverEncodedAsZero() {
        FeatureProjectionV1.ProjectionResult result = projectionWithoutSimpleReturn();

        assertThat(result.values()).filteredOn(value -> value.name().equals("ohlc.simple_return"))
                .first().satisfies(value -> {
                    assertThat(value).isInstanceOf(NumericFeatureValue.class);
                    assertThat(value.status()).isEqualTo(FeatureMissingness.INSUFFICIENT_DATA);
                    assertThat(((NumericFeatureValue) value).value()).isNull();
        });
    }

    @ParameterizedTest
    @EnumSource(value = MarketStructureAvailability.class, names = {"STALE", "INSUFFICIENT", "INVALID", "UNAVAILABLE"})
    void mapsNonAvailableStructureStatusToTheFeatureGroup(MarketStructureAvailability availability) {
        FeatureProjectionV1.ProjectionResult result = project(
                ohlc(java.util.Map.of("rangePercentage", new BigDecimal("12.5"))),
                List.of(structure("FOUR_HOURS", availability), structure("ONE_HOUR")));

        assertThat(result.groups()).filteredOn(group -> group.group() == FeatureGroup.STRUCTURE)
                .first().extracting(FeatureGroupStatus::status)
                .isEqualTo(switch (availability) {
                    case STALE -> FeatureMissingness.STALE_INPUT;
                    case INSUFFICIENT -> FeatureMissingness.INSUFFICIENT_DATA;
                    case INVALID -> FeatureMissingness.INVALID_INPUT;
                    case UNAVAILABLE -> FeatureMissingness.UNAVAILABLE;
                    case AVAILABLE -> FeatureMissingness.PRESENT;
                });
    }

    @Test
    void marksMissingAnalyzerGroupsAsUnavailable() {
        FeatureProjectionV1.ProjectionResult result = FeatureProjectionV1.project(
                ANALYSIS_ID, MARKET_ID, CUTOFF, EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                "context", List.of(), List.of(), List.of(), CUTOFF);

        assertThat(result.groups()).extracting(FeatureGroupStatus::status)
                .containsExactly(FeatureMissingness.UNAVAILABLE,
                        FeatureMissingness.UNAVAILABLE, FeatureMissingness.UNAVAILABLE);
        assertThat(result.values()).allSatisfy(value ->
                assertThat(value.status()).isIn(FeatureMissingness.UNAVAILABLE,
                FeatureMissingness.INSUFFICIENT_DATA));
    }

    @Test
    void rejectsMultipleArtifactsOfTheSameContentType() {
        ProducedArtifact first = ohlc(java.util.Map.of("rangePercentage", new BigDecimal("12.5")));
        ProducedArtifact second = ohlc(java.util.Map.of("rangePercentage", new BigDecimal("15.0")));

        assertThatThrownBy(() -> projectWithDuplicateOhlc(first, second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple artifacts for DeterministicMeasurements");
    }

    private FeatureProjectionV1.ProjectionResult projectWithDuplicateOhlc(
            ProducedArtifact first, ProducedArtifact second) {
        return FeatureProjectionV1.project(
                ANALYSIS_ID, MARKET_ID, CUTOFF, EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                "context", List.of(first, second), List.of(), List.of(), CUTOFF);
    }

    private FeatureProjectionV1.ProjectionResult projection() {
        return project(ohlc(java.util.Map.of(
                "rangePercentage", new BigDecimal("12.5"),
                "simpleReturn", new BigDecimal("0.025"))));
    }

    private FeatureProjectionV1.ProjectionResult projectionWithoutSimpleReturn() {
        return project(ohlc(java.util.Map.of("rangePercentage", new BigDecimal("12.5"))));
    }

    private FeatureProjectionV1.ProjectionResult project(ProducedArtifact ohlc) {
        return project(ohlc, List.of(structure("FOUR_HOURS"), structure("ONE_HOUR")));
    }

    private FeatureProjectionV1.ProjectionResult project(
            ProducedArtifact ohlc, List<ProducedArtifact> structures) {
        var assessment = TrendContextTestFixtures.assessment();
        var trendContent = new TrendContextCapabilityContent(
                assessment, "AVAILABLE", List.of(), java.util.Map.of(),
                assessment.assessmentAt(), assessment.cutOffAt(),
                assessment.inputFingerprint(), assessment.assessmentFingerprint());
        return FeatureProjectionV1.project(
                ANALYSIS_ID, MARKET_ID, CUTOFF, EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                 ArtifactFingerprint.ofInputs(List.of("context")).value(),
                 List.of(ohlc), structures,
                 List.of(artifact(ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT,
                         ProductionArtifactTypes.V1, trendContent, "TREND_CONTEXT")), CUTOFF);
    }

    private ProducedArtifact ohlc(java.util.Map<String, BigDecimal> values) {
        return artifact(ProductionArtifactTypes.OHLC_RANGE_ANALYSIS, ProductionArtifactTypes.V1,
                new DeterministicMeasurements("OHLC", "test", values, CUTOFF), "FIFTEEN_MINUTES");
    }

    private ProducedArtifact structure(String interval) {
        return structure(interval, MarketStructureAvailability.AVAILABLE);
    }

    private ProducedArtifact structure(String interval, MarketStructureAvailability availability) {
        MarketStructureResult result = mock(MarketStructureResult.class);
        when(result.interval()).thenReturn(interval);
        when(result.availability()).thenReturn(availability);
        when(result.cutOffAt()).thenReturn(CUTOFF);
        when(result.retained()).thenReturn(List.of());
        when(result.suppressed()).thenReturn(List.of());
        when(result.latestRelation(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
        when(result.resultFingerprint()).thenReturn(ArtifactFingerprint.ofInputs(List.of(interval)).value());
        when(result.inputFingerprint()).thenReturn(ArtifactFingerprint.ofInputs(List.of("input", interval)).value());
        when(result.ruleVersion()).thenReturn("confirmed-swing-v1");
        when(result.algorithmId()).thenReturn("CONFIRMED_SWING_V1");
        when(result.policyId()).thenReturn("CAPTURED");
        when(result.policyVersion()).thenReturn("1");
        when(result.parameterFingerprint()).thenReturn(ArtifactFingerprint.ofInputs(List.of("parameters", interval)).value());
        return artifact(ProductionArtifactTypes.MARKET_STRUCTURE, ProductionArtifactTypes.MARKET_STRUCTURE_V2,
                new MarketStructureArtifactContent(result, CUTOFF, result.inputFingerprint()), interval);
    }

    private ProducedArtifact artifact(com.hope.trading.market_intelligence.domain.capability.ArtifactType type,
                                      com.hope.trading.market_intelligence.domain.capability.ArtifactVersion version,
                                      Object content, String timeframe) {
        StoredArtifact artifact = new StoredArtifact(
                new ArtifactCacheKey(new ArtifactIdentity(type.value(), "test-capability", version.value()),
                        ArtifactScope.publicMarket(MARKET_ID, timeframe, AnalysisExecutionMode.ACTIVE),
                        ArtifactFingerprint.empty(), ArtifactFingerprint.ofInputs(List.of(type.value(), timeframe))),
                (com.hope.trading.market_intelligence.domain.artifact.ArtifactContent) content,
                ArtifactFreshness.validUntil(CUTOFF, CUTOFF.plusSeconds(60), "test"),
                new ArtifactProvenance("test-capability", version.value(), ANALYSIS_ID, CUTOFF,
                         Set.of(), Set.of()).withEvidenceFingerprint(type.value().toLowerCase() + "-evidence"),
                 com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality.COMPLETE, version);
        return new ProducedArtifact(type, version, artifact);
    }
}
