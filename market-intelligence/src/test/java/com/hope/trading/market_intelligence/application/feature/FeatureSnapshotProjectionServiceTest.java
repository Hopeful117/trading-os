package com.hope.trading.market_intelligence.application.feature;

import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.ArtifactPersistencePort;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.ProducedArtifact;
import com.hope.trading.market_intelligence.domain.execution.*;
import com.hope.trading.market_intelligence.domain.execution.ExecutionTestFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FeatureSnapshotProjectionServiceTest {
    private static final UUID EXECUTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MARKET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CUTOFF = Instant.parse("2026-10-15T10:00:00Z");
    private static final String CONTEXT_FINGERPRINT =
            "0000000000000000000000000000000000000000000000000000000000000000";

    @Test
    void rejectsArtifactFromAnotherExecution() {
        AnalysisExecutionRepository executions = mock(AnalysisExecutionRepository.class);
        ArtifactPersistencePort artifacts = mock(ArtifactPersistencePort.class);
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(executions.findById(EXECUTION_ID)).thenReturn(java.util.Optional.of(execution()));
        when(artifacts.find(any(), any(), any())).thenReturn(List.of(artifact(UUID.randomUUID(), CUTOFF)));

        FeatureSnapshotProjectionService service = new FeatureSnapshotProjectionService(
                executions, artifacts, snapshots, Clock.fixed(CUTOFF, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.project(EXECUTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not linked");
    }

    @Test
    void rejectsArtifactWithAnotherEvaluationCutoff() {
        AnalysisExecutionRepository executions = mock(AnalysisExecutionRepository.class);
        ArtifactPersistencePort artifacts = mock(ArtifactPersistencePort.class);
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(executions.findById(EXECUTION_ID)).thenReturn(java.util.Optional.of(execution()));
        when(artifacts.find(any(), any(), any())).thenReturn(List.of(artifact(EXECUTION_ID, CUTOFF.plusSeconds(1))));

        FeatureSnapshotProjectionService service = new FeatureSnapshotProjectionService(
                executions, artifacts, snapshots, Clock.fixed(CUTOFF, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.project(EXECUTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cutoff");
    }

    @Test
    void rejectsArtifactFromAnotherAnalysisContext() {
        AnalysisExecutionRepository executions = mock(AnalysisExecutionRepository.class);
        ArtifactPersistencePort artifacts = mock(ArtifactPersistencePort.class);
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(executions.findById(EXECUTION_ID)).thenReturn(java.util.Optional.of(execution()));
        when(artifacts.find(any(), any(), any())).thenReturn(
                List.of(artifact(EXECUTION_ID, CUTOFF, "different-context")));

        FeatureSnapshotProjectionService service = new FeatureSnapshotProjectionService(
                executions, artifacts, snapshots, Clock.fixed(CUTOFF, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.project(EXECUTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("context");
    }

    @Test
    void acceptsCapabilityArtifactConsumedByTheParentAnalysisExecution() {
        AnalysisExecutionRepository executions = mock(AnalysisExecutionRepository.class);
        ArtifactPersistencePort artifacts = mock(ArtifactPersistencePort.class);
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(executions.findById(EXECUTION_ID)).thenReturn(java.util.Optional.of(execution()));
        when(artifacts.find(any(), eq(ProductionArtifactTypes.OHLC_RANGE_ANALYSIS), any())).thenReturn(
                List.of(artifact(UUID.randomUUID(), CUTOFF, CONTEXT_FINGERPRINT, true)));
        when(artifacts.find(any(), eq(ProductionArtifactTypes.MARKET_STRUCTURE), any())).thenReturn(List.of());
        when(artifacts.find(any(), eq(ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT), any())).thenReturn(List.of());

        FeatureSnapshotProjectionService service = new FeatureSnapshotProjectionService(
                executions, artifacts, snapshots, Clock.fixed(CUTOFF, ZoneOffset.UTC));

        service.project(EXECUTION_ID);

        verify(snapshots).saveIfAbsent(any());
    }

    @Test
    void acceptsEvidenceEndingBeforeTheEvaluationCutoff() {
        AnalysisExecutionRepository executions = mock(AnalysisExecutionRepository.class);
        ArtifactPersistencePort artifacts = mock(ArtifactPersistencePort.class);
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(executions.findById(EXECUTION_ID)).thenReturn(java.util.Optional.of(execution()));
        when(artifacts.find(any(), eq(ProductionArtifactTypes.OHLC_RANGE_ANALYSIS), any())).thenReturn(
                List.of(artifact(EXECUTION_ID, CUTOFF.minusSeconds(900), CONTEXT_FINGERPRINT, true)));
        when(artifacts.find(any(), eq(ProductionArtifactTypes.MARKET_STRUCTURE), any())).thenReturn(List.of());
        when(artifacts.find(any(), eq(ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT), any())).thenReturn(List.of());

        FeatureSnapshotProjectionService service = new FeatureSnapshotProjectionService(
                executions, artifacts, snapshots, Clock.fixed(CUTOFF, ZoneOffset.UTC));

        service.project(EXECUTION_ID);

        verify(snapshots).saveIfAbsent(any());
    }

    private AnalysisExecution execution() {
        return AnalysisExecution.restore(
                EXECUTION_ID, new IdempotencyKey("feature-projection-test"), AnalysisExecutionStatus.COMPLETED,
                AnalysisResultQuality.COMPLETE, ExecutionTestFixtures.policy(), CUTOFF, CUTOFF, CUTOFF.plusSeconds(60),
                CUTOFF, List.of(), RetryMetadata.none(1),
                new AnalysisExecutionProvenance(MARKET_ID, AnalysisExecutionMode.PASSIVE, "", "v1",
                        null, null, null, CUTOFF, EvidenceAcquisitionPolicy.STRICT_OBSERVED),
                AnalysisTraceMetadata.empty(),
                ExecutionTestFixtures.result(EXECUTION_ID, MARKET_ID, CUTOFF), null);
    }

    private ProducedArtifact artifact(UUID producingExecutionId, Instant cutoff) {
        return artifact(producingExecutionId, cutoff, CONTEXT_FINGERPRINT);
    }

    private ProducedArtifact artifact(UUID producingExecutionId, Instant cutoff, String contextFingerprint) {
        return artifact(producingExecutionId, cutoff, contextFingerprint, false);
    }

    private ProducedArtifact artifact(
            UUID producingExecutionId, Instant cutoff, String contextFingerprint, boolean consumedByParent) {
        DeterministicMeasurements content = new DeterministicMeasurements(
                "OHLC", "test", java.util.Map.of("rangePercentage", new BigDecimal("12.5")), cutoff);
        ArtifactProvenance provenance = new ArtifactProvenance(
                "test-capability", "v1", producingExecutionId, cutoff, Set.of(), Set.of())
                .withContextFingerprint(contextFingerprint);
        if (consumedByParent) {
            provenance = provenance.reusedBy(EXECUTION_ID);
        }
        StoredArtifact stored = new StoredArtifact(
                new ArtifactCacheKey(
                        new ArtifactIdentity(ProductionArtifactTypes.OHLC_RANGE_ANALYSIS.value(), "test-capability", "v1"),
                        ArtifactScope.publicMarket(MARKET_ID, "FIFTEEN_MINUTES", AnalysisExecutionMode.PASSIVE),
                        ArtifactFingerprint.empty(), ArtifactFingerprint.ofInputs(List.of("ohlc"))),
                content, ArtifactFreshness.validUntil(cutoff, cutoff.plusSeconds(60), "test"),
                provenance,
                 AnalysisResultQuality.COMPLETE, ProductionArtifactTypes.V1);
        return new ProducedArtifact(ProductionArtifactTypes.OHLC_RANGE_ANALYSIS,
                ProductionArtifactTypes.V1, stored);
    }
}
