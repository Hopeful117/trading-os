package com.hope.trading.market_intelligence.application.feature;

import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.ArtifactPersistencePort;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.artifact.DeterministicMeasurements;
import com.hope.trading.market_intelligence.domain.artifact.StoredArtifact;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureArtifactContent;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class FeatureSnapshotProjectionService {
    private final AnalysisExecutionRepository executions;
    private final ArtifactPersistencePort artifacts;
    private final FeatureSnapshotRepository snapshots;
    private final Clock clock;

    public FeatureSnapshotProjectionService(
            AnalysisExecutionRepository executions,
            ArtifactPersistencePort artifacts,
            FeatureSnapshotRepository snapshots,
            Clock clock
    ) {
        this.executions = executions;
        this.artifacts = artifacts;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    public FeatureSnapshot project(UUID analysisExecutionId) {
        AnalysisExecution execution = executions.findById(analysisExecutionId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis execution not found: " + analysisExecutionId));
        if (execution.provenance().evidencePolicy() != EvidenceAcquisitionPolicy.STRICT_OBSERVED) {
            throw new IllegalArgumentException("Feature projection requires STRICT_OBSERVED evidence");
        }
        String contextFingerprint = execution.result()
                .map(value -> value.executionMetadata().contextFingerprint())
                .filter(value -> value != null && !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("Analysis execution has no context fingerprint"));
        var cutoff = Objects.requireNonNull(execution.provenance().evaluationCutoff(),
                "Strict execution has no evaluation cutoff");
        List<com.hope.trading.market_intelligence.domain.capability.ProducedArtifact> ohlc = artifacts.find(
                analysisExecutionId, ProductionArtifactTypes.OHLC_RANGE_ANALYSIS, ProductionArtifactTypes.V1);
        List<com.hope.trading.market_intelligence.domain.capability.ProducedArtifact> structures = artifacts.find(
                analysisExecutionId, ProductionArtifactTypes.MARKET_STRUCTURE, ProductionArtifactTypes.MARKET_STRUCTURE_V2);
        List<com.hope.trading.market_intelligence.domain.capability.ProducedArtifact> trend = artifacts.find(
                analysisExecutionId, ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT, ProductionArtifactTypes.V1);
        validateArtifacts(analysisExecutionId, execution.provenance().marketId(), cutoff, contextFingerprint, ohlc);
        validateArtifacts(analysisExecutionId, execution.provenance().marketId(), cutoff, contextFingerprint, structures);
        validateArtifacts(analysisExecutionId, execution.provenance().marketId(), cutoff, contextFingerprint, trend);
        FeatureProjectionV1.ProjectionResult projection = FeatureProjectionV1.project(
                analysisExecutionId, execution.provenance().marketId(), cutoff,
                execution.provenance().evidencePolicy(), contextFingerprint,
                ohlc, structures, trend, clock.instant());
        FeatureSnapshot snapshot = new FeatureSnapshot(
                UUID.randomUUID(), analysisExecutionId, execution.provenance().marketId(), cutoff,
                execution.provenance().evidencePolicy(), projection.schema().version(), contextFingerprint,
                projection.values(), projection.groups(), projection.provenance(), projection.fingerprint(), clock.instant());
        return snapshots.saveIfAbsent(snapshot);
    }

    private void validateArtifacts(
            UUID executionId,
            UUID marketId,
            java.time.Instant cutoff,
            String contextFingerprint,
            List<com.hope.trading.market_intelligence.domain.capability.ProducedArtifact> producedArtifacts
    ) {
        producedArtifacts.forEach(produced -> {
            StoredArtifact artifact = produced.artifact();
            if (!marketId.equals(artifact.key().scope().marketId())) {
                throw new IllegalStateException("Feature artifact market does not match analysis execution");
            }
            if (!contextFingerprint.equals(artifact.provenance().contextFingerprint())) {
                throw new IllegalStateException("Feature artifact context does not match analysis execution");
            }
            UUID producingExecutionId = artifact.provenance().producingExecutionId();
            if (producingExecutionId == null
                    || (!executionId.equals(producingExecutionId)
                    && !artifact.provenance().reusedByExecutions().contains(executionId))) {
                throw new IllegalStateException("Feature artifact is not linked to analysis execution");
            }
            java.time.Instant artifactCutoff = cutoffOf(artifact);
            if (artifactCutoff.isAfter(cutoff)) {
                throw new IllegalStateException("Feature artifact cutoff is after analysis execution cutoff");
            }
        });
    }

    private java.time.Instant cutoffOf(StoredArtifact artifact) {
        return switch (artifact.content()) {
            case DeterministicMeasurements value -> value.observedAt();
            case MarketStructureArtifactContent value -> value.cutOffAt();
            case TrendContextCapabilityContent value -> value.cutOffAt();
            default -> throw new IllegalStateException(
                    "Unsupported feature artifact content: " + artifact.content().getClass().getName());
        };
    }
}
