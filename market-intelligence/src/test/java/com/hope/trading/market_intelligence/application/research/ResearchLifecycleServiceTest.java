package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.feature.FeatureSnapshotProjectionService;
import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationService;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionProvenance;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResearchLifecycleServiceTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-10-16T10:03:27Z");

    @Test
    void alignsCutoffToUtcCadence() {
        assertThat(ResearchLifecycleService.alignedCutoff(NOW, Duration.ofHours(1)))
                .isEqualTo(Instant.parse("2026-10-16T10:00:00Z"));
    }

    @Test
    void rejectsCadenceThatIsNotAWholePositiveSecond() {
        Duration zero = Duration.ZERO;
        Duration fractionalSecond = Duration.ofMillis(500);
        assertThatThrownBy(() -> ResearchLifecycleService.alignedCutoff(NOW, zero))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResearchLifecycleService.alignedCutoff(NOW, fractionalSecond))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitsStrictObservationOnlyOnceForAnAlignedCutoff() {
        ResearchLifecycleProperties properties = validProperties();
        AnalysisExecutionService executions = mock(AnalysisExecutionService.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(executionRepository.findByResearchScope(any(), any(), any(), any())).thenReturn(List.of());
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(any(), any(), any(), any(), any())).thenReturn(List.of());

        ResearchLifecycleService service = new ResearchLifecycleService(
                properties, executions, executionRepository, mock(FeatureSnapshotProjectionService.class),
                snapshots, mock(MarketOutcomeEvaluationService.class), Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        var request = org.mockito.ArgumentCaptor.forClass(IntelligenceAnalysisRequest.class);
        verify(executions).create(request.capture(), any(IdempotencyKey.class), anyString(), anyString());
        assertThat(request.getValue().evidencePolicy()).isEqualTo(EvidenceAcquisitionPolicy.STRICT_OBSERVED);
        assertThat(request.getValue().evaluationCutoff())
                .isEqualTo(Instant.parse("2026-10-16T10:00:00Z"));
        assertThat(request.getValue().marketId()).isEqualTo(MARKET_ID);
    }

    @Test
    void doesNotSubmitWhenTheLifecycleIsDisabled() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        AnalysisExecutionService executions = mock(AnalysisExecutionService.class);
        ResearchLifecycleService service = new ResearchLifecycleService(
                properties, executions, mock(AnalysisExecutionRepository.class),
                mock(FeatureSnapshotProjectionService.class), mock(FeatureSnapshotRepository.class),
                mock(MarketOutcomeEvaluationService.class), Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        verifyNoInteractions(executions);
    }

    @Test
    void ignoresSubmissionFailuresSoTheLifecycleCanContinue() {
        ResearchLifecycleProperties properties = validProperties();
        AnalysisExecutionService executions = mock(AnalysisExecutionService.class);
        doThrow(new RuntimeException("submission unavailable")).when(executions)
                .create(any(), any(), anyString(), anyString());
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(executionRepository.findByResearchScope(any(), any(), any(), any())).thenReturn(List.of());
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(any(), any(), any(), any(), any())).thenReturn(List.of());

        ResearchLifecycleService service = new ResearchLifecycleService(
                properties, executions, executionRepository, mock(FeatureSnapshotProjectionService.class),
                snapshots, mock(MarketOutcomeEvaluationService.class), Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        verify(executions).create(any(), any(), anyString(), anyString());
    }

    @Test
    void evaluatesEachConfiguredHorizonOnlyAfterSnapshotMaturity() {
        ResearchLifecycleProperties properties = validProperties();
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(mock(
                com.hope.trading.market_intelligence.domain.execution.AnalysisExecution.class)));
        when(executionRepository.findByResearchScope(any(), any(), any(), any())).thenReturn(List.of());
        FeatureSnapshot snapshot = mock(FeatureSnapshot.class);
        when(snapshot.marketId()).thenReturn(MARKET_ID);
        when(snapshot.evaluationCutoff()).thenReturn(Instant.parse("2026-10-15T00:00:00Z"));
        when(snapshot.analysisExecutionId()).thenReturn(UUID.randomUUID());
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(any(), any(), any(), any(), any())).thenReturn(List.of(snapshot));
        MarketOutcomeEvaluationService outcomes = mock(MarketOutcomeEvaluationService.class);
        doThrow(new RuntimeException("outcome unavailable")).when(outcomes).evaluate(any());

        ResearchLifecycleService service = new ResearchLifecycleService(
                properties, mock(AnalysisExecutionService.class), executionRepository,
                mock(FeatureSnapshotProjectionService.class), snapshots, outcomes,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        verify(outcomes, times(3)).evaluate(any());
    }

    @Test
    void ignoresProjectionFailuresAndContinuesTheLifecycle() {
        ResearchLifecycleProperties properties = validProperties();
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(mock(AnalysisExecution.class)));
        AnalysisExecution execution = mock(AnalysisExecution.class);
        when(execution.status()).thenReturn(AnalysisExecutionStatus.PARTIALLY_COMPLETED);
        when(execution.result()).thenReturn(Optional.of(mock(ConsolidatedIntelligence.class)));
        when(execution.executionId()).thenReturn(UUID.randomUUID());
        AnalysisExecutionProvenance provenance = mock(AnalysisExecutionProvenance.class);
        when(provenance.marketId()).thenReturn(MARKET_ID);
        when(provenance.evaluationCutoff()).thenReturn(NOW);
        when(execution.provenance()).thenReturn(provenance);
        when(executionRepository.findByResearchScope(any(), any(), any(), any())).thenReturn(List.of(execution));
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(any(), any(), any(), any(), any())).thenReturn(List.of());
        FeatureSnapshotProjectionService projections = mock(FeatureSnapshotProjectionService.class);
        doThrow(new RuntimeException("projection unavailable")).when(projections).project(any());

        ResearchLifecycleService service = new ResearchLifecycleService(
                properties, mock(AnalysisExecutionService.class), executionRepository,
                projections, snapshots, mock(MarketOutcomeEvaluationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        verify(projections).project(execution.executionId());
    }

    @Test
    void skipsHorizonsThatHaveNotMaturedYet() {
        ResearchLifecycleProperties properties = validProperties();
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(mock(
                com.hope.trading.market_intelligence.domain.execution.AnalysisExecution.class)));
        when(executionRepository.findByResearchScope(any(), any(), any(), any())).thenReturn(List.of());
        FeatureSnapshot snapshot = mock(FeatureSnapshot.class);
        when(snapshot.marketId()).thenReturn(MARKET_ID);
        when(snapshot.evaluationCutoff()).thenReturn(NOW.minus(Duration.ofHours(2)));
        when(snapshot.analysisExecutionId()).thenReturn(UUID.randomUUID());
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(any(), any(), any(), any(), any())).thenReturn(List.of(snapshot));
        MarketOutcomeEvaluationService outcomes = mock(MarketOutcomeEvaluationService.class);

        ResearchLifecycleService service = new ResearchLifecycleService(
                properties, mock(AnalysisExecutionService.class), executionRepository,
                mock(FeatureSnapshotProjectionService.class), snapshots, outcomes,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        verify(outcomes).evaluate(any());
    }

    @Test
    void projectsCompletedExecutionsWithResults() {
        ResearchLifecycleProperties properties = validProperties();
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(mock(AnalysisExecution.class)));
        AnalysisExecution execution = mock(AnalysisExecution.class);
        when(execution.status()).thenReturn(AnalysisExecutionStatus.COMPLETED);
        when(execution.result()).thenReturn(Optional.of(mock(ConsolidatedIntelligence.class)));
        when(execution.executionId()).thenReturn(UUID.randomUUID());
        when(executionRepository.findByResearchScope(any(), any(), any(), any())).thenReturn(List.of(execution));
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(any(), any(), any(), any(), any())).thenReturn(List.of());
        FeatureSnapshotProjectionService projections = mock(FeatureSnapshotProjectionService.class);

        ResearchLifecycleService service = new ResearchLifecycleService(
                properties, mock(AnalysisExecutionService.class), executionRepository,
                projections, snapshots, mock(MarketOutcomeEvaluationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        verify(projections).project(execution.executionId());
    }

    private ResearchLifecycleProperties validProperties() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1), Duration.ofHours(4), Duration.ofHours(24)));
        return properties;
    }
}
