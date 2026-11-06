package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.feature.FeatureSnapshotProjectionService;
import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationRequest;
import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationService;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import com.hope.trading.market_intelligence.domain.research.ResearchFeatureRequirements;
import com.hope.trading.market_intelligence.domain.research.ResearchFeatureSnapshotKey;
import com.hope.trading.market_intelligence.domain.research.ResearchOutcomeKey;
import com.hope.trading.market_intelligence.domain.research.ResearchPopulationSpec;
import com.hope.trading.market_intelligence.domain.research.ResearchSamplingCadence;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.application.research.ResearchDatasetExportService;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class HistoricalReconstructionService {
    private static final Logger log = LoggerFactory.getLogger(HistoricalReconstructionService.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final String PROJECTION = "feature-projection-v1";
    private final HistoricalReconstructionPreflight preflight;
    private final AnalysisExecutionService executions;
    private final AnalysisExecutionRepository executionRepository;
    private final FeatureSnapshotProjectionService projections;
    private final FeatureSnapshotRepository snapshots;
    private final MarketOutcomeEvaluationService outcomes;
    private final ResearchDatasetExportService exports;
    private final Clock clock;

    public HistoricalReconstructionService(
            HistoricalReconstructionPreflight preflight,
            AnalysisExecutionService executions,
            AnalysisExecutionRepository executionRepository,
            FeatureSnapshotProjectionService projections,
            FeatureSnapshotRepository snapshots,
            MarketOutcomeEvaluationService outcomes,
            ResearchDatasetExportService exports,
            Clock clock
    ) {
        this.preflight = preflight;
        this.executions = executions;
        this.executionRepository = executionRepository;
        this.projections = projections;
        this.snapshots = snapshots;
        this.outcomes = outcomes;
        this.exports = exports;
        this.clock = clock;
    }

    public Result run(UUID marketId, Instant cutoff, Path outputRoot, String runId,
                       boolean confirmation, String approvalReference) {
        if (!confirmation) throw new IllegalArgumentException("Historical reconstruction confirmation is required");
        if (approvalReference == null || approvalReference.isBlank()) {
            throw new IllegalArgumentException("Human approval reference is required");
        }
        String operatorPrincipal = ProcessHandle.current().info().user().orElse(null);
        if (operatorPrincipal == null || operatorPrincipal.isBlank()) {
            throw new IllegalStateException("Trusted local operator principal is unavailable");
        }
        Path output = resolveOutput(outputRoot, runId);
        HistoricalReconstructionPreflight.Result selected = preflight.verify(marketId, cutoff);
        AnalysisExecution execution = null;
        String terminalStatus = "FAILED_BEFORE_EXECUTION";
        try {
            IdempotencyKey key = new IdempotencyKey("historical-reconstruction:v2:" + marketId + ":"
                    + cutoff + ":PT1H:STRICT_OBSERVED:" + digest(selectionBinding(selected)));
            execution = executions.createHistorical(
                    new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.PASSIVE,
                            HistoricalReconstructionPolicy.OBJECTIVE, null, null, null, cutoff,
                            EvidenceAcquisitionPolicy.STRICT_OBSERVED),
                    key, key.value(), key.value(), selectionBinding(selected));
            log.info("historical_reconstruction_audit phase=START operator={} approvalReference={} marketId={} cutoff={} runId={} executionId={} requestFingerprint={} selectionFingerprint={}",
                    operatorPrincipal, approvalReference, marketId, cutoff, runId, execution.executionId(),
                    execution.provenance().reconstructionRequestFingerprint(), selected.selectionFingerprint());
            execution = awaitTerminal(execution.executionId());
            terminalStatus = execution.status().name();
            if (execution.status() != AnalysisExecutionStatus.COMPLETED) {
                return new Result(execution.executionId(), null, null, execution.status().name(), output);
            }
            HistoricalReconstructionPreflight.Result acquiredSelection = preflight.verify(marketId, cutoff);
            if (!selected.selectedEvidence().equals(acquiredSelection.selectedEvidence())
                    || !selected.selectionFingerprint().equals(acquiredSelection.selectionFingerprint())) {
                throw new IllegalStateException(
                        "Historical evidence selection changed during reconstruction");
            }
            FeatureSnapshot snapshot = projections.project(execution.executionId());
            ObservedMarketOutcome outcome = outcomes.evaluate(new MarketOutcomeEvaluationRequest(
                    marketId, cutoff, new OutcomeHorizon(Duration.ofHours(1)), execution.executionId()));
            if (outcome.status() != ObservedMarketOutcomeStatus.AVAILABLE) {
                terminalStatus = "OUTCOME_" + outcome.status().name();
                return new Result(execution.executionId(), snapshot.fingerprint(), outcome.outcomeFingerprint(),
                        terminalStatus, output);
            }
            ResearchPopulationSpec population = new ResearchPopulationSpec(
                    "historical-reconstruction-v1", Set.of(marketId), cutoff, cutoff.plusNanos(1),
                    PROJECTION, EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                    outcome.outcomeDefinitionVersion(), Set.of(new OutcomeHorizon(Duration.ofHours(1))),
                    ResearchSamplingCadence.ONE_PER_FEATURE_SNAPSHOT, Set.of(),
                    java.util.Map.of(new ResearchFeatureSnapshotKey(marketId, cutoff), snapshot.fingerprint()),
                    ResearchFeatureRequirements.q1(),
                    java.util.Map.of(new ResearchOutcomeKey(marketId, cutoff, new OutcomeHorizon(Duration.ofHours(1))),
                    outcome.outcomeFingerprint()));
            exports.export(population, output);
            terminalStatus = "EXPORTED";
            return new Result(execution.executionId(), snapshot.fingerprint(), outcome.outcomeFingerprint(), terminalStatus, output);
        } finally {
            log.info("historical_reconstruction_audit phase=FINAL operator={} approvalReference={} marketId={} cutoff={} runId={} executionId={} terminalStatus={} outputDirectory={}",
                    operatorPrincipal, approvalReference, marketId, cutoff, runId,
                    execution == null ? null : execution.executionId(), terminalStatus, output);
        }
    }

    private AnalysisExecution awaitTerminal(UUID executionId) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            AnalysisExecution current = executionRepository.findById(executionId)
                    .orElseThrow(() -> new IllegalStateException("Reconstruction execution disappeared"));
            if (current.status().isTerminal()) return current;
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                executions.cancel(executionId);
                throw new IllegalStateException("Historical reconstruction interrupted", interrupted);
            }
        }
        executions.cancel(executionId);
        return executionRepository.findById(executionId).orElseThrow();
    }

    private Path resolveOutput(Path root, String runId) {
        if (root == null || runId == null || runId.isBlank() || runId.contains("..")
                || runId.contains("/") || runId.contains("\\")) {
            throw new IllegalArgumentException("Invalid reconstruction output location");
        }
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("Reconstruction output root is not a trusted directory");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(runId).normalize();
        if (!resolved.startsWith(normalizedRoot) || Files.exists(resolved)
                || Files.isSymbolicLink(resolved)) {
            throw new IllegalArgumentException("Reconstruction output path is not available");
        }
        return resolved;
    }

    private String selectionBinding(HistoricalReconstructionPreflight.Result selected) {
        return selected.selectionFingerprint() + "|references="
                + String.join(",", selected.selectedEvidence());
    }

    private String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Result(UUID executionId, String snapshotFingerprint, String outcomeFingerprint,
                         String status, Path outputDirectory) { }
}
