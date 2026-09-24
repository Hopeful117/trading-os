package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryObservationRepository;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
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

        TrendContextReadModel model = service(observations, executions).find(
                TrendContextTestFixtures.MARKET_ID);

        assertThat(model.operationalStatus()).isEqualTo("AVAILABLE");
        assertThat(model.assessmentPresent()).isTrue();
        assertThat(model.assessmentValidity()).isEqualTo("VALID");
        assertThat(model.assessment()).isSameAs(assessment);
        assertThat(model.lastSuccessfulAssessment()).isSameAs(assessment);
        assertThat(model.observationId()).isEqualTo(observation.id());
        assertThat(model.observationVersion()).isEqualTo(1L);
    }

    @Test
    void currentFailurePreservesHistoricalAssessmentWithoutCallingItCurrent() {
        InMemoryObservationRepository observations = new InMemoryObservationRepository();
        TrendContextAssessment assessment = assessment(TrendAttention.CONTEXTUALLY_ATTRACTIVE);
        saveObservation(observations, assessment, NOW.minusSeconds(7200));
        AnalysisExecutionRepository executions = repository(
                execution(AnalysisExecutionStatus.FAILED, NOW.minusSeconds(60)));

        TrendContextReadModel model = service(observations, executions).find(
                TrendContextTestFixtures.MARKET_ID);

        assertThat(model.operationalStatus()).isEqualTo("UNAVAILABLE");
        assertThat(model.assessmentPresent()).isFalse();
        assertThat(model.assessment()).isNull();
        assertThat(model.lastSuccessfulAssessment()).isSameAs(assessment);
        assertThat(model.assessmentValidity()).isEqualTo("EXPIRED");
    }

    @Test
    void failureWithoutHistoryHasNoFabricatedAssessment() {
        AnalysisExecutionRepository executions = repository(
                execution(AnalysisExecutionStatus.FAILED, NOW.minusSeconds(60)));

        TrendContextReadModel model = service(
                new InMemoryObservationRepository(), executions).find(
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
        saveObservation(observations, assessment, NOW.minusSeconds(900));
        AnalysisExecutionRepository executions = repository(
                execution(AnalysisExecutionStatus.COMPLETED, NOW.minusSeconds(1200)));

        TrendContextReadModel model = service(observations, executions).find(
                TrendContextTestFixtures.MARKET_ID);

        assertThat(model.operationalStatus()).isEqualTo("AVAILABLE");
        assertThat(model.assessmentPresent()).isTrue();
        assertThat(model.assessment().attention()).isEqualTo(attention);
    }

    private TrendContextReadService service(
            ObservationRepository observations, AnalysisExecutionRepository executions) {
        return new TrendContextReadService(
                observations, executions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AnalysisExecutionRepository repository(AnalysisExecution execution) {
        AnalysisExecutionRepository repository = mock(AnalysisExecutionRepository.class);
        when(repository.findLatestByMarketId(TrendContextTestFixtures.MARKET_ID))
                .thenReturn(Optional.of(execution));
        return repository;
    }

    private AnalysisExecution execution(AnalysisExecutionStatus status, Instant completedAt) {
        AnalysisExecution execution = mock(AnalysisExecution.class);
        when(execution.status()).thenReturn(status);
        when(execution.capabilities()).thenReturn(List.of("trend-context-analysis"));
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
        TrendContextCapabilityContent content = new TrendContextCapabilityContent(
                assessment, "AVAILABLE", List.of(), Map.of(),
                assessment.assessmentAt(), assessment.cutOffAt(),
                assessment.inputFingerprint(), assessment.assessmentFingerprint());
        Observation observation = new ObservationFactory().create(
                UUID.randomUUID(), 1, "BTC/EUR", new ObservationType("TREND_CONTEXT"),
                "Trend Context", "test", Set.of("trend-context"), "TREND_CONTEXT",
                createdAt, createdAt, createdAt.plusSeconds(3600), null,
                TrendContextObservationRule.VERSION,
                List.of(ObservationTestFixtures.evidence(BigDecimal.ONE)),
                new TrendContextObservationPayload(content));
        observations.save(observation);
        return observation;
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
