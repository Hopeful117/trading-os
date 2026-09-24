package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.application.capability.*;
import com.hope.trading.market_intelligence.application.port.CapabilityExecutionRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.observation.*;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TrendContextObservationIntegrationTest {
    private static final String INSTRUMENT = "BTC/EUR";

    @Test
    void capabilityEvidenceBecomesTypedImmutableObservationAndReplays() {
        InMemoryCapabilityExecutionRepository executions = new InMemoryCapabilityExecutionRepository();
        InMemoryObservationRepository observations = new InMemoryObservationRepository();
        ObservationBuilder builder = new ObservationBuilder(
                executions, observations, new ObservationFactory(),
                Clock.fixed(TrendContextTestFixtures.ASSESSMENT_AT, ZoneOffset.UTC));
        UUID analysisId = UUID.randomUUID();
        TrendContextAssessment assessmentA = assessment(fingerprint('a'), fingerprint('b'));
        CapabilityExecution executionA = completed(analysisId, assessmentA);
        executions.save(executionA);

        Observation first = builder.build(analysisId, INSTRUMENT, new TrendContextObservationRule());
        TrendContextObservationPayload payload =
                (TrendContextObservationPayload) first.payload().orElseThrow();

        assertThat(first.type()).isEqualTo(new ObservationType("TREND_CONTEXT"));
        assertThat(payload.content().assessment()).isSameAs(assessmentA);
        assertThat(payload.content().inputFingerprint()).isEqualTo(fingerprint('a'));
        assertThat(payload.content().assessmentFingerprint()).isEqualTo(fingerprint('b'));
        assertThat(payload.content().assessment().attention()).isEqualTo(TrendAttention.WATCH);
        assertThat(payload.content().cutOffAt()).isEqualTo(assessmentA.cutOffAt());
        assertThat(payload.content().operationalStatus()).isEqualTo("AVAILABLE");
        assertThat(first.evidence()).singleElement().satisfies(evidence ->
                assertThat(evidence.capabilityResult().artifacts()).singleElement()
                        .satisfies(trace -> assertThat(trace.inputFingerprint()).isEqualTo(fingerprint('a'))));
        assertThat(first.id().toString()).isNotEqualTo(fingerprint('a'));
        assertThat(first.id().toString()).isNotEqualTo(fingerprint('b'));

        assertThat(builder.build(analysisId, INSTRUMENT, new TrendContextObservationRule()))
                .isSameAs(first);
        assertThat(observations.findActive()).containsExactly(first);
    }

    @Test
    void materialEvidenceCreatesNewVersionAndSupersedesPreviousPayload() {
        InMemoryCapabilityExecutionRepository executions = new InMemoryCapabilityExecutionRepository();
        InMemoryObservationRepository observations = new InMemoryObservationRepository();
        ObservationBuilder builder = new ObservationBuilder(
                executions, observations, new ObservationFactory(),
                Clock.fixed(TrendContextTestFixtures.ASSESSMENT_AT, ZoneOffset.UTC));
        UUID firstAnalysis = UUID.randomUUID();
        UUID secondAnalysis = UUID.randomUUID();
        Observation first;

        executions.save(completed(firstAnalysis, assessment(fingerprint('a'), fingerprint('b'))));
        first = builder.build(firstAnalysis, INSTRUMENT, new TrendContextObservationRule());
        executions.save(completed(secondAnalysis, assessment(fingerprint('c'), fingerprint('d'))));
        Observation second = builder.build(secondAnalysis, INSTRUMENT, new TrendContextObservationRule());

        assertThat(second.version()).isEqualTo(first.version() + 1);
        assertThat(second.lineageId()).isEqualTo(first.lineageId());
        assertThat(second.supersedes()).contains(first.id());
        assertThat(observations.findById(first.id()).orElseThrow().status())
                .isEqualTo(ObservationStatus.SUPERSEDED);
        assertThat(observations.findById(first.id()).orElseThrow().payload()).isPresent();
        assertThat(observations.findActive()).containsExactly(second);
    }

    @Test
    void noAssessmentDoesNotCreateTrendContextObservation() {
        CapabilityExecutionRepository executions = mock(CapabilityExecutionRepository.class);
        ObservationBuilder builder = mock(ObservationBuilder.class);
        UUID analysisId = UUID.randomUUID();
        CapabilityMetadata metadata = metadata();
        CapabilityExecution failed = CapabilityExecution.created(analysisId, metadata,
                        TrendContextTestFixtures.ASSESSMENT_AT)
                .transitionTo(CapabilityExecutionState.READY, TrendContextTestFixtures.ASSESSMENT_AT)
                .transitionTo(CapabilityExecutionState.RUNNING, TrendContextTestFixtures.ASSESSMENT_AT)
                .fail(new CapabilityFailure("input", "INPUT_UNAVAILABLE", "missing", false,
                        null, null, TrendContextTestFixtures.ASSESSMENT_AT, Map.of()),
                        TrendContextTestFixtures.ASSESSMENT_AT);
        when(executions.findByAnalysisExecutionId(analysisId)).thenReturn(List.of(failed));

        new TrendContextObservationService(executions, builder)
                .buildIfAssessmentExists(analysisId, INSTRUMENT);

        verifyNoInteractions(builder);
    }

    @Test
    void contextuallyAttractiveAssessmentStopsAtObservationBoundary() {
        InMemoryCapabilityExecutionRepository executions = new InMemoryCapabilityExecutionRepository();
        InMemoryObservationRepository observations = new InMemoryObservationRepository();
        ObservationBuilder builder = new ObservationBuilder(
                executions, observations, new ObservationFactory(),
                Clock.fixed(TrendContextTestFixtures.ASSESSMENT_AT, ZoneOffset.UTC));
        UUID analysisId = UUID.randomUUID();
        TrendContextAssessment assessment = assessment(fingerprint('a'), fingerprint('b'));
        when(assessment.attention()).thenReturn(TrendAttention.CONTEXTUALLY_ATTRACTIVE);
        executions.save(completed(analysisId, assessment));

        Observation observation = builder.build(analysisId, INSTRUMENT,
                new TrendContextObservationRule());

        assertThat(observation.payload()).isPresent();
        assertThat(observation.type()).isEqualTo(new ObservationType("TREND_CONTEXT"));
        assertThat(observations.findActive()).containsExactly(observation);
        assertThat(observation.evidence()).singleElement()
                .extracting(item -> item.capabilityResult().capabilityId())
                .isEqualTo(TrendContextAnalysisCapability.CAPABILITY_ID);
    }

    @Test
    void observationPersistenceFailureIsPropagatedAndNotReportedAsDurableSuccess() {
        CapabilityExecutionRepository executions = new InMemoryCapabilityExecutionRepository();
        ObservationRepository observations = mock(ObservationRepository.class);
        when(observations.findByInstrument(INSTRUMENT)).thenReturn(List.of());
        when(observations.save(any())).thenThrow(new IllegalStateException("persistence unavailable"));
        ObservationBuilder builder = new ObservationBuilder(
                executions, observations, new ObservationFactory(),
                Clock.fixed(TrendContextTestFixtures.ASSESSMENT_AT, ZoneOffset.UTC));
        UUID analysisId = UUID.randomUUID();
        executions.save(completed(analysisId, assessment(fingerprint('e'), fingerprint('f'))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                builder.build(analysisId, INSTRUMENT, new TrendContextObservationRule()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("persistence unavailable");
        verify(observations).save(any());
    }

    private CapabilityExecution completed(UUID analysisId, TrendContextAssessment assessment) {
        CapabilityExecution created = CapabilityExecution.created(
                analysisId, metadata(), TrendContextTestFixtures.ASSESSMENT_AT.minusSeconds(2));
        return created
                .transitionTo(CapabilityExecutionState.READY, TrendContextTestFixtures.ASSESSMENT_AT.minusSeconds(2))
                .transitionTo(CapabilityExecutionState.RUNNING, TrendContextTestFixtures.ASSESSMENT_AT.minusSeconds(1))
                .complete(result(created.id(), assessment), TrendContextTestFixtures.ASSESSMENT_AT);
    }

    private CapabilityResult result(UUID executionId, TrendContextAssessment assessment) {
        TrendContextCapabilityContent content = new TrendContextCapabilityContent(
                assessment, "AVAILABLE", List.of(), Map.of(),
                assessment.assessmentAt(), assessment.cutOffAt(),
                assessment.inputFingerprint(), assessment.assessmentFingerprint());
        StoredArtifact artifact = new StoredArtifact(
                new ArtifactCacheKey(
                        new ArtifactIdentity(ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT.value(),
                                TrendContextAnalysisCapability.CAPABILITY_ID, "1.0.0"),
                        ArtifactScope.publicMarket(assessment.marketId(), "TREND_CONTEXT", AnalysisExecutionMode.ACTIVE),
                        ArtifactFingerprint.empty(), new ArtifactFingerprint(assessment.inputFingerprint())),
                content,
                ArtifactFreshness.validUntil(assessment.assessmentAt(),
                        assessment.assessmentAt().plusSeconds(3600), assessment.inputFingerprint()),
                new ArtifactProvenance(TrendContextAnalysisCapability.CAPABILITY_ID, "1.0.0",
                        executionId, assessment.assessmentAt(), Set.of(), Set.of()),
                AnalysisResultQuality.COMPLETE);
        return new CapabilityResult(
                metadata().producedContributions(),
                List.of(new ProducedArtifact(ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT,
                        ProductionArtifactTypes.V1, artifact)),
                Map.of(), List.of(), CapabilityCompleteness.COMPLETE);
    }

    private CapabilityMetadata metadata() {
        return new CapabilityMetadata(
                new CapabilityId(TrendContextAnalysisCapability.CAPABILITY_ID),
                new CapabilityVersion(TrendContextAnalysisCapability.CAPABILITY_VERSION),
                CapabilityCategory.DETERMINISTIC, ExecutionPolicy.ON_DEMAND,
                RetryPolicy.disabled(), List.of(),
                List.of(new ProducedContribution.ArtifactContribution(
                        ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT,
                        ProductionArtifactTypes.V1, Set.of())),
                java.time.Duration.ofSeconds(5), null);
    }

    private TrendContextAssessment assessment(String inputFingerprint, String assessmentFingerprint) {
        TrendContextAssessment assessment = mock(TrendContextAssessment.class);
        when(assessment.marketId()).thenReturn(TrendContextTestFixtures.MARKET_ID);
        when(assessment.assessmentAt()).thenReturn(TrendContextTestFixtures.ASSESSMENT_AT);
        when(assessment.cutOffAt()).thenReturn(TrendContextTestFixtures.ASSESSMENT_AT);
        when(assessment.inputFingerprint()).thenReturn(inputFingerprint);
        when(assessment.assessmentFingerprint()).thenReturn(assessmentFingerprint);
        when(assessment.attention()).thenReturn(TrendAttention.WATCH);
        when(assessment.findings()).thenReturn(List.of());
        return assessment;
    }

    private String fingerprint(char value) {
        return String.valueOf(value).repeat(64);
    }
}
