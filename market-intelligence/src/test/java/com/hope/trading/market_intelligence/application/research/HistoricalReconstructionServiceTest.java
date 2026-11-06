package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.feature.FeatureSnapshotProjectionService;
import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationService;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.HistoricalReconstructionLifecycleRepository;
import com.hope.trading.market_intelligence.adapter.persistence.InMemoryHistoricalReconstructionLifecycleRepository;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionProvenance;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.ExecutionOrigin;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HistoricalReconstructionServiceTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-11-06T15:45:00Z");
    private static final Instant NOW = Instant.parse("2026-11-06T18:00:00Z");

    @TempDir
    Path outputRoot;

    @Test
    void projectionFailureIsPersistedAsFailedReconstructionAfterCompletedAnalysis() {
        InMemoryHistoricalReconstructionLifecycleRepository lifecycles = new InMemoryHistoricalReconstructionLifecycleRepository();
        AnalysisExecution execution = completedExecution();
        UUID executionId = execution.executionId();
        HistoricalReconstructionPreflight preflight = mock(HistoricalReconstructionPreflight.class);
        when(preflight.verify(MARKET_ID, CUTOFF)).thenReturn(new HistoricalReconstructionPreflight.Result(
                List.of("feature-evidence"), "selection"));
        AnalysisExecutionService executions = mock(AnalysisExecutionService.class);
        when(executions.registerHistorical(any(), any(), anyString(), anyString(), anyString())).thenReturn(execution);
        when(executions.dispatchHistorical(execution)).thenReturn(execution);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
        FeatureSnapshotProjectionService projections = mock(FeatureSnapshotProjectionService.class);
        doThrow(new IllegalStateException("structure artifact unavailable")).when(projections).project(executionId);

        HistoricalReconstructionService service = service(preflight, executions, executionRepository, projections,
                mock(FeatureSnapshotRepository.class), mock(MarketOutcomeEvaluationService.class),
                mock(ResearchDatasetExportService.class), lifecycles);

        assertThatThrownBy(() -> service.run(MARKET_ID, CUTOFF, outputRoot, "projection-failure", true, "approval-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("structure artifact unavailable");

        var lifecycle = lifecycles.findByExecutionId(execution.executionId()).orElseThrow();
        assertThat(execution.status()).isEqualTo(AnalysisExecutionStatus.COMPLETED);
        assertThat(lifecycle.status()).isEqualTo(com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecycleStatus.FAILED);
        assertThat(lifecycle.phase()).isEqualTo(com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecyclePhase.ANALYSIS_COMPLETED);
    }

    @Test
    void successfulReconstructionIsReturnedIdempotentlyWithoutExportingAgain() {
        InMemoryHistoricalReconstructionLifecycleRepository lifecycles = new InMemoryHistoricalReconstructionLifecycleRepository();
        AnalysisExecution execution = completedExecution();
        UUID executionId = execution.executionId();
        HistoricalReconstructionPreflight preflight = mock(HistoricalReconstructionPreflight.class);
        when(preflight.verify(MARKET_ID, CUTOFF)).thenReturn(new HistoricalReconstructionPreflight.Result(
                List.of("feature-evidence"), "selection"));
        AnalysisExecutionService executions = mock(AnalysisExecutionService.class);
        when(executions.registerHistorical(any(), any(), anyString(), anyString(), anyString())).thenReturn(execution);
        when(executions.dispatchHistorical(execution)).thenReturn(execution);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
        FeatureSnapshot snapshot = mock(FeatureSnapshot.class);
        when(snapshot.fingerprint()).thenReturn("snapshot-fingerprint");
        FeatureSnapshotProjectionService projections = mock(FeatureSnapshotProjectionService.class);
        when(projections.project(executionId)).thenReturn(snapshot);
        ObservedMarketOutcome outcome = mock(ObservedMarketOutcome.class);
        when(outcome.status()).thenReturn(ObservedMarketOutcomeStatus.AVAILABLE);
        when(outcome.outcomeFingerprint()).thenReturn("outcome-fingerprint");
        when(outcome.outcomeDefinitionVersion()).thenReturn("market-outcome-v1");
        MarketOutcomeEvaluationService outcomes = mock(MarketOutcomeEvaluationService.class);
        when(outcomes.evaluate(any())).thenReturn(outcome);
        ResearchDatasetExportService exports = mock(ResearchDatasetExportService.class);
        when(exports.export(any(), any())).thenReturn(new ResearchDatasetExportService.ExportResult(1, 1, 0));
        HistoricalReconstructionService service = service(preflight, executions, executionRepository, projections,
                mock(FeatureSnapshotRepository.class), outcomes, exports, lifecycles);

        HistoricalReconstructionService.Result first = service.run(
                MARKET_ID, CUTOFF, outputRoot, "success-1", true, "approval-1");
        HistoricalReconstructionService.Result second = service.run(
                MARKET_ID, CUTOFF, outputRoot, "success-2", true, "approval-1");

        assertThat(first.status()).isEqualTo("EXPORTED");
        assertThat(second.status()).isEqualTo("EXPORTED");
        verify(exports, times(1)).export(any(), any());
    }

    private HistoricalReconstructionService service(
            HistoricalReconstructionPreflight preflight,
            AnalysisExecutionService executions,
            AnalysisExecutionRepository executionRepository,
            FeatureSnapshotProjectionService projections,
            FeatureSnapshotRepository snapshots,
            MarketOutcomeEvaluationService outcomes,
            ResearchDatasetExportService exports,
            HistoricalReconstructionLifecycleRepository lifecycles
    ) {
        return new HistoricalReconstructionService(preflight, executions, executionRepository, projections,
                snapshots, outcomes, exports, lifecycles, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AnalysisExecution completedExecution() {
        return mockCompletedExecution(UUID.randomUUID());
    }

    private AnalysisExecution mockCompletedExecution(UUID executionId) {
        AnalysisExecution execution = mock(AnalysisExecution.class);
        when(execution.executionId()).thenReturn(executionId);
        when(execution.status()).thenReturn(AnalysisExecutionStatus.COMPLETED);
        when(execution.provenance()).thenReturn(new AnalysisExecutionProvenance(
                MARKET_ID, AnalysisExecutionMode.PASSIVE, HistoricalReconstructionPolicy.OBJECTIVE, "v1",
                null, null, null, CUTOFF, EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                ExecutionOrigin.HISTORICAL_RECONSTRUCTION, "request-fingerprint", "selection"));
        return execution;
    }
}
