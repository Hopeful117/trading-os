package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.feature.FeatureSnapshotProjectionService;
import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationService;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.adapter.marketdata.OrderBookRetentionClient;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionProvenance;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
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
                snapshots, mock(MarketOutcomeEvaluationService.class), mock(ResearchArtifactPopulationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        var request = org.mockito.ArgumentCaptor.forClass(IntelligenceAnalysisRequest.class);
        verify(executions).create(request.capture(), any(IdempotencyKey.class), anyString(), anyString());
        assertThat(request.getValue().evidencePolicy()).isEqualTo(EvidenceAcquisitionPolicy.STRICT_OBSERVED);
        assertThat(request.getValue().evaluationCutoff())
                .isEqualTo(Instant.parse("2026-10-16T10:00:00Z"));
        assertThat(request.getValue().marketId()).isEqualTo(MARKET_ID);
    }

    @Test
    void populatesArtifactsWithTheAlignedObservationCutoff() {
        ResearchLifecycleProperties properties = validProperties();
        AnalysisExecutionService executions = mock(AnalysisExecutionService.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(mock(AnalysisExecution.class)));
        when(executionRepository.findByResearchScope(any(), any(), any(), any())).thenReturn(List.of());
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(any(), any(), any(), any(), any())).thenReturn(List.of());
        ResearchArtifactPopulationService population = mock(ResearchArtifactPopulationService.class);

        ResearchLifecycleService service = new ResearchLifecycleService(
                properties, executions, executionRepository, mock(FeatureSnapshotProjectionService.class),
                snapshots, mock(MarketOutcomeEvaluationService.class), population,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        verify(population).populate(Instant.parse("2026-10-16T10:00:00Z"));
    }

    @Test
    void populationFailureDoesNotStopTheResearchLifecycle() {
        ResearchLifecycleProperties properties = validProperties();
        AnalysisExecutionService executions = mock(AnalysisExecutionService.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(executionRepository.findByResearchScope(any(), any(), any(), any())).thenReturn(List.of());
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(any(), any(), any(), any(), any())).thenReturn(List.of());
        ResearchArtifactPopulationService population = mock(ResearchArtifactPopulationService.class);
        doThrow(new RuntimeException("population unavailable")).when(population).populate(any());

        ResearchLifecycleService service = new ResearchLifecycleService(
                properties, executions, executionRepository, mock(FeatureSnapshotProjectionService.class),
                snapshots, mock(MarketOutcomeEvaluationService.class), population,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        verify(executions).create(any(), any(IdempotencyKey.class), anyString(), anyString());
    }

    @Test
    void doesNotSubmitWhenTheLifecycleIsDisabled() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        AnalysisExecutionService executions = mock(AnalysisExecutionService.class);
        ResearchLifecycleService service = new ResearchLifecycleService(
                properties, executions, mock(AnalysisExecutionRepository.class),
                mock(FeatureSnapshotProjectionService.class), mock(FeatureSnapshotRepository.class),
                mock(MarketOutcomeEvaluationService.class), mock(ResearchArtifactPopulationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

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
                snapshots, mock(MarketOutcomeEvaluationService.class), mock(ResearchArtifactPopulationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

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
                mock(ResearchArtifactPopulationService.class), Clock.fixed(NOW, ZoneOffset.UTC));

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
                mock(ResearchArtifactPopulationService.class), Clock.fixed(NOW, ZoneOffset.UTC));

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
                mock(ResearchArtifactPopulationService.class), Clock.fixed(NOW, ZoneOffset.UTC));

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
                mock(ResearchArtifactPopulationService.class), Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        verify(projections).project(execution.executionId());
    }

    @Test
    void releasesReplayDependencyAfterAllConfiguredOutcomesAreTerminal() {
        ResearchLifecycleProperties properties = validProperties();
        properties.setArtifactPopulationEnabled(true);
        properties.setArtifactPopulationDepth(25);
        properties.setArtifactPopulationMaxPerRun(10);
        properties.setArtifactPopulationPromotionReason("test");
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(mock(AnalysisExecution.class)));
        when(executionRepository.findByResearchScope(any(), any(), any(), any())).thenReturn(List.of());
        FeatureSnapshot snapshot = mock(FeatureSnapshot.class);
        Instant cutoff = Instant.parse("2026-10-16T08:00:00Z");
        when(snapshot.marketId()).thenReturn(MARKET_ID);
        when(snapshot.evaluationCutoff()).thenReturn(cutoff);
        when(snapshot.analysisExecutionId()).thenReturn(UUID.randomUUID());
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(any(), any(), any(), any(), any())).thenReturn(List.of(snapshot));
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        List<Duration> horizons = properties.getOutcomeHorizons();
        List<ObservedMarketOutcome> terminalOutcomes = horizons.stream()
                .map(duration -> terminalOutcome(cutoff, duration)).toList();
        when(outcomes.findByMarkets(any())).thenReturn(terminalOutcomes);
        OrderBookRetentionClient retention = mock(OrderBookRetentionClient.class);

        ResearchLifecycleService service = new ResearchLifecycleService(
                properties, mock(AnalysisExecutionService.class), executionRepository,
                mock(FeatureSnapshotProjectionService.class), snapshots,
                mock(MarketOutcomeEvaluationService.class), mock(ResearchArtifactPopulationService.class),
                retention, outcomes, Clock.fixed(NOW, ZoneOffset.UTC));

        service.runOnce();

        verify(retention).release("order-book-replay:v1:market=11111111-1111-1111-1111-111111111111:depth=25:cutoff=2026-10-16T08:00:00Z",
                new OrderBookRetentionClient.ReleaseRequest("research-lifecycle:durable-snapshot-and-terminal-outcomes"));
    }

    private ObservedMarketOutcome terminalOutcome(Instant cutoff, Duration duration) {
        ObservedMarketOutcome outcome = mock(ObservedMarketOutcome.class);
        when(outcome.marketId()).thenReturn(MARKET_ID);
        when(outcome.evaluationCutoff()).thenReturn(cutoff);
        when(outcome.horizon()).thenReturn(new OutcomeHorizon(duration));
        when(outcome.status()).thenReturn(ObservedMarketOutcomeStatus.AVAILABLE);
        return outcome;
    }

    private ResearchLifecycleProperties validProperties() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1), Duration.ofHours(4), Duration.ofHours(24)));
        return properties;
    }
}
