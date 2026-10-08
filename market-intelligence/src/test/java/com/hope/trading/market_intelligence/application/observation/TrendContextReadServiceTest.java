package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryObservationRepository;
import com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.CapabilityExecutionRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;
import com.hope.trading.market_intelligence.domain.capability.CapabilityId;
import com.hope.trading.market_intelligence.domain.observation.*;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import com.hope.trading.market_intelligence.domain.execution.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TrendContextReadServiceTest {
    private static final Instant NOW = TrendContextTestFixtures.ASSESSMENT_AT.plusSeconds(1800);

    @Test
    void currentSuccessfulExecutionExposesCurrentTypedAssessment() {
        InMemoryObservationRepository observations = new InMemoryObservationRepository();
        TrendContextAssessment assessment = assessment(TrendAttention.WATCH);
        Observation observation = saveObservation(observations, assessment, NOW.minusSeconds(900));
        AnalysisExecution execution = execution(AnalysisExecutionStatus.COMPLETED,
                NOW.minusSeconds(1200));
        AnalysisExecutionRepository executions = repository(execution);
        CapabilityExecutionRepository capabilityExecutions = capabilityRepository(execution,
                observationCapabilityId(observation));

        TrendContextReadModel model = service(observations, executions, capabilityExecutions).find(
                TrendContextTestFixtures.MARKET_ID);

        assertThat(model.operationalStatus()).isEqualTo("AVAILABLE");
        assertThat(model.assessmentPresent()).isTrue();
        assertThat(model.assessmentValidity()).isEqualTo("VALID");
        assertThat(model.assessment()).isSameAs(assessment);
        assertThat(model.lastSuccessfulAssessment()).isSameAs(assessment);
        assertThat(model.observationId()).isEqualTo(observation.id());
        assertThat(model.observationVersion()).isEqualTo(1L);
        assertThat(model.analysisExecutionId()).isEqualTo(execution.executionId());
        assertThat(model.capabilityExecutionIds()).containsExactly(observationCapabilityId(observation));
    }

    @Test
    void currentFailurePreservesHistoricalAssessmentWithoutCallingItCurrent() {
        InMemoryObservationRepository observations = new InMemoryObservationRepository();
        TrendContextAssessment assessment = assessment(TrendAttention.CONTEXTUALLY_ATTRACTIVE);
        saveObservation(observations, assessment, NOW.minusSeconds(7200));
        AnalysisExecutionRepository executions = repository(
                execution(AnalysisExecutionStatus.FAILED, NOW.minusSeconds(60)));

        TrendContextReadModel model = service(observations, executions,
                mock(CapabilityExecutionRepository.class)).find(
                TrendContextTestFixtures.MARKET_ID);

        assertThat(model.operationalStatus()).isEqualTo("UNAVAILABLE");
        assertThat(model.assessmentPresent()).isFalse();
        assertThat(model.assessment()).isNull();
        assertThat(model.lastSuccessfulAssessment()).isSameAs(assessment);
        assertThat(model.assessmentValidity()).isEqualTo("EXPIRED");
    }

    @Test
    void validHistoryWithoutRelevantExecutionIsNotReportedAsAvailable() {
        InMemoryObservationRepository observations = new InMemoryObservationRepository();
        TrendContextAssessment assessment = assessment(TrendAttention.WATCH);
        Observation observation = saveObservation(observations, assessment, NOW.minusSeconds(900));
        AnalysisExecutionRepository executions = mock(AnalysisExecutionRepository.class);
        when(executions.findLatestByMarketId(TrendContextTestFixtures.MARKET_ID))
                .thenReturn(Optional.empty());

        TrendContextReadModel model = service(observations, executions,
                mock(CapabilityExecutionRepository.class)).find(
                TrendContextTestFixtures.MARKET_ID);

        assertThat(model.operationalStatus()).isEqualTo("MISSING");
        assertThat(model.assessmentPresent()).isFalse();
        assertThat(model.lastSuccessfulAssessment()).isSameAs(assessment);
        assertThat(model.capabilityExecutionIds())
                .containsExactly(observationCapabilityId(observation));
    }

    @Test
    void failureWithoutHistoryHasNoFabricatedAssessment() {
        AnalysisExecutionRepository executions = repository(
                execution(AnalysisExecutionStatus.FAILED, NOW.minusSeconds(60)));

        TrendContextReadModel model = service(
                new InMemoryObservationRepository(), executions,
                mock(CapabilityExecutionRepository.class)).find(
                TrendContextTestFixtures.MARKET_ID);

        assertThat(model.operationalStatus()).isEqualTo("UNAVAILABLE");
        assertThat(model.assessmentPresent()).isFalse();
        assertThat(model.assessment()).isNull();
        assertThat(model.lastSuccessfulAssessment()).isNull();
        assertThat(model.assessmentValidity()).isEqualTo("NONE");
    }

    @ParameterizedTest
    @EnumSource(value = TrendAttention.class, names = {"NO_SETUP", "WATCH", "UNKNOWN"})
    void analyticalAttentionRemainsSeparateFromOperationalAvailability(TrendAttention attention) {
        InMemoryObservationRepository observations = new InMemoryObservationRepository();
        TrendContextAssessment assessment = assessment(attention);
        Observation observation = saveObservation(observations, assessment, NOW.minusSeconds(900));
        AnalysisExecutionRepository executions = repository(
                execution(AnalysisExecutionStatus.COMPLETED, NOW.minusSeconds(1200)));

        TrendContextReadModel model = service(observations, executions,
                capabilityRepository(executions.findLatestByMarketId(
                        TrendContextTestFixtures.MARKET_ID).orElseThrow(),
                        observationCapabilityId(observation))).find(
                TrendContextTestFixtures.MARKET_ID);

        assertThat(model.operationalStatus()).isEqualTo("AVAILABLE");
        assertThat(model.assessmentPresent()).isTrue();
        assertThat(model.assessment().attention()).isEqualTo(attention);
    }

    @Test
    void lateObservationFromAnotherExecutionIsNotCurrent() {
        InMemoryObservationRepository observations = new InMemoryObservationRepository();
        TrendContextAssessment assessment = assessment(TrendAttention.WATCH);
        UUID oldCapabilityExecutionId = UUID.randomUUID();
        Observation observation = saveObservation(observations, assessment, NOW.minusSeconds(60),
                oldCapabilityExecutionId, "AVAILABLE");
        AnalysisExecution latestExecution = execution(AnalysisExecutionStatus.COMPLETED,
                NOW.minusSeconds(120));
        AnalysisExecutionRepository executions = repository(latestExecution);
        CapabilityExecutionRepository capabilityExecutions = capabilityRepository(
                latestExecution, UUID.randomUUID());

        TrendContextReadModel model = service(observations, executions, capabilityExecutions).find(
                TrendContextTestFixtures.MARKET_ID);

        assertThat(model.assessmentPresent()).isFalse();
        assertThat(model.operationalStatus()).isEqualTo("STALE");
        assertThat(model.lastSuccessfulAssessment()).isSameAs(assessment);
    }

    @Test
    void currentDegradedCapabilityIsExposedAsDegraded() {
        InMemoryObservationRepository observations = new InMemoryObservationRepository();
        TrendContextAssessment assessment = assessment(TrendAttention.WATCH);
        Observation observation = saveObservation(observations, assessment, NOW.minusSeconds(900),
                UUID.randomUUID(), "DEGRADED");
        AnalysisExecution execution = execution(AnalysisExecutionStatus.COMPLETED,
                NOW.minusSeconds(1200));
        AnalysisExecutionRepository executions = repository(execution);

        TrendContextReadModel model = service(observations, executions,
                capabilityRepository(execution, observationCapabilityId(observation))).find(
                TrendContextTestFixtures.MARKET_ID);

        assertThat(model.operationalStatus()).isEqualTo("DEGRADED");
        assertThat(model.assessmentPresent()).isTrue();
    }

    private TrendContextReadService service(
            ObservationRepository observations, AnalysisExecutionRepository executions,
            CapabilityExecutionRepository capabilityExecutions) {
        return new TrendContextReadService(
                observations, executions, capabilityExecutions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AnalysisExecutionRepository repository(AnalysisExecution execution) {
        AnalysisExecutionRepository repository = mock(AnalysisExecutionRepository.class);
        when(repository.findLatestByMarketId(TrendContextTestFixtures.MARKET_ID))
                .thenReturn(Optional.of(execution));
        return repository;
    }

    private AnalysisExecution execution(AnalysisExecutionStatus status, Instant completedAt) {
        AnalysisExecution execution = mock(AnalysisExecution.class);
        when(execution.executionId()).thenReturn(UUID.randomUUID());
        when(execution.status()).thenReturn(status);
        when(execution.capabilities()).thenReturn(List.of("trend-context-analysis"));
        when(execution.requestedAt()).thenReturn(completedAt.minusSeconds(600));
        when(execution.completedAt()).thenReturn(Optional.of(completedAt));
        when(execution.provenance()).thenReturn(new AnalysisExecutionProvenance(
                TrendContextTestFixtures.MARKET_ID,
                com.hope.trading.market_intelligence.domain.AnalysisExecutionMode.ACTIVE,
                "test", "v1"));
        return execution;
    }

    private Observation saveObservation(
            InMemoryObservationRepository observations,
            TrendContextAssessment assessment, Instant createdAt) {
        return saveObservation(observations, assessment, createdAt, UUID.randomUUID(), "AVAILABLE");
    }

    private Observation saveObservation(
            InMemoryObservationRepository observations,
            TrendContextAssessment assessment, Instant createdAt,
            UUID capabilityExecutionId, String operationalStatus) {
        TrendContextCapabilityContent content = new TrendContextCapabilityContent(
                assessment, operationalStatus, List.of(), Map.of(),
                assessment.assessmentAt(), assessment.cutOffAt(),
                assessment.inputFingerprint(), assessment.assessmentFingerprint());
        Observation observation = new ObservationFactory().create(
                UUID.randomUUID(), 1, "BTC/EUR", new ObservationType("TREND_CONTEXT"),
                "Trend Context", "test", Set.of("trend-context"), "TREND_CONTEXT",
                createdAt, createdAt, createdAt.plusSeconds(3600), null,
                TrendContextObservationRule.RULE_VERSION,
                List.of(ObservationTestFixtures.evidence(capabilityExecutionId, BigDecimal.ONE)),
                new TrendContextObservationPayload(content));
        observations.save(observation);
        return observation;
    }

    private UUID observationCapabilityId(Observation observation) {
        return observation.evidence().getFirst().capabilityResult().capabilityExecutionId();
    }

    private CapabilityExecutionRepository capabilityRepository(
            AnalysisExecution execution, UUID capabilityExecutionId) {
        CapabilityExecutionRepository repository = mock(CapabilityExecutionRepository.class);
        CapabilityExecution capability = mock(CapabilityExecution.class);
        when(capability.id()).thenReturn(capabilityExecutionId);
        when(capability.state()).thenReturn(com.hope.trading.market_intelligence.domain.capability.CapabilityExecutionState.COMPLETED);
        when(capability.capabilityId()).thenReturn(new CapabilityId(
                TrendContextAnalysisCapability.CAPABILITY_ID));
        when(repository.findByAnalysisExecutionId(execution.executionId())).thenReturn(List.of(capability));
        return repository;
    }

    private TrendContextAssessment assessment(TrendAttention attention) {
        TrendContextAssessment assessment = mock(TrendContextAssessment.class);
        when(assessment.marketId()).thenReturn(TrendContextTestFixtures.MARKET_ID);
        when(assessment.assessmentAt()).thenReturn(TrendContextTestFixtures.ASSESSMENT_AT);
        when(assessment.cutOffAt()).thenReturn(TrendContextTestFixtures.ASSESSMENT_AT);
        when(assessment.inputFingerprint()).thenReturn("input-" + attention);
        when(assessment.assessmentFingerprint()).thenReturn("assessment-" + attention);
        when(assessment.attention()).thenReturn(attention);
        return assessment;
    }
}
