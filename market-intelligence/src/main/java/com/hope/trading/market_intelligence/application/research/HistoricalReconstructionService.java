package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.feature.FeatureSnapshotProjectionService;
import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationRequest;
import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationService;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.HistoricalReconstructionLifecycleRepository;
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
import com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecycle;
import com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecyclePhase;
import com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecycleStatus;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class HistoricalReconstructionService {
    private static final Logger log = LoggerFactory.getLogger(HistoricalReconstructionService.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final Duration LEASE = TIMEOUT.plusSeconds(30);
    private static final String PROJECTION = "feature-projection-v1";
    private final HistoricalReconstructionPreflight preflight;
    private final AnalysisExecutionService executions;
    private final AnalysisExecutionRepository executionRepository;
    private final FeatureSnapshotProjectionService projections;
    private final FeatureSnapshotRepository snapshots;
    private final MarketOutcomeEvaluationService outcomes;
    private final ResearchDatasetExportService exports;
    private final HistoricalReconstructionLifecycleRepository lifecycles;
    private final Clock clock;

    public HistoricalReconstructionService(
            HistoricalReconstructionPreflight preflight,
            AnalysisExecutionService executions,
            AnalysisExecutionRepository executionRepository,
            FeatureSnapshotProjectionService projections,
            FeatureSnapshotRepository snapshots,
            MarketOutcomeEvaluationService outcomes,
            ResearchDatasetExportService exports,
            HistoricalReconstructionLifecycleRepository lifecycles,
            Clock clock
    ) {
        this.preflight = preflight;
        this.executions = executions;
        this.executionRepository = executionRepository;
        this.projections = projections;
        this.snapshots = snapshots;
        this.outcomes = outcomes;
        this.exports = exports;
        this.lifecycles = lifecycles;
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
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        Path output = resolveOutput(outputRoot, runId);
        HistoricalReconstructionPreflight.Result selected = null;
        AnalysisExecution execution = null;
        HistoricalReconstructionLifecycle lifecycle = null;
        IdempotencyKey idempotencyKey = null;
        UUID leaseId = UUID.randomUUID();
        String terminalStatus = "FAILED_BEFORE_EXECUTION";
        ExecutorService phases = Executors.newVirtualThreadPerTaskExecutor();
        try {
            selected = runPhase(phases, () -> preflight.verify(marketId, cutoff), deadline, "preflight");
            ensureWithinDeadline(deadline, "preflight");
            IdempotencyKey key = new IdempotencyKey("historical-reconstruction:v2:" + marketId + ":"
                    + cutoff + ":PT1H:STRICT_OBSERVED:" + digest(selectionBinding(selected)));
            idempotencyKey = key;
            execution = executions.registerHistorical(
                    new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.PASSIVE,
                            HistoricalReconstructionPolicy.OBJECTIVE, null, null, null, cutoff,
                            EvidenceAcquisitionPolicy.STRICT_OBSERVED)
                            .withHistoricalEvidenceSelection(String.join("\n", selected.selectedEvidence())),
                     key, key.value(), key.value(), selectionBinding(selected));
            lifecycle = lifecycles.findByExecutionId(execution.executionId()).orElse(null);
            if (lifecycle != null && lifecycle.status() != HistoricalReconstructionLifecycleStatus.RUNNING) {
                terminalStatus = lifecycle.status() == HistoricalReconstructionLifecycleStatus.SUCCEEDED
                        ? "EXPORTED" : lifecycle.status().name();
                return existingResult(lifecycle, output);
            }
            boolean owner = false;
            if (lifecycle == null) {
                lifecycle = HistoricalReconstructionLifecycle.started(
                        execution.executionId(), leaseId, output.toString(), clock.instant());
                owner = lifecycles.saveIfAbsent(lifecycle);
                if (!owner) {
                    lifecycle = lifecycles.findByExecutionId(execution.executionId()).orElseThrow();
                }
            }
            if (!owner && lifecycle.status() == HistoricalReconstructionLifecycleStatus.RUNNING) {
                if (!lifecycle.updatedAt().plus(LEASE).isAfter(clock.instant())
                        && lifecycles.claimIfStale(execution.executionId(), leaseId,
                        clock.instant().minus(LEASE), clock.instant())) {
                    lifecycle = lifecycle.claimed(leaseId, clock.instant());
                    owner = true;
                }
            }
            if (!owner) {
                return new Result(execution.executionId(), lifecycle.snapshotFingerprint(),
                        lifecycle.outcomeFingerprint(), "RUNNING", output);
            }
            execution = executions.dispatchHistorical(execution);
            log.info("historical_reconstruction_audit phase=START operator={} approvalReference={} marketId={} cutoff={} runId={} executionId={} requestFingerprint={} selectionFingerprint={}",
                    operatorPrincipal, approvalReference, marketId, cutoff, runId, execution.executionId(),
                    execution.provenance().reconstructionRequestFingerprint(), selected.selectionFingerprint());
            execution = awaitTerminal(execution.executionId(), deadline);
            ensureWithinDeadline(deadline, "execution");
            terminalStatus = execution.status().name();
            if (execution.status() != AnalysisExecutionStatus.COMPLETED) {
                recordFailure(lifecycle, HistoricalReconstructionLifecycleStatus.FAILED,
                        "ANALYSIS_" + execution.status().name(), "Deterministic analysis did not complete");
                return new Result(execution.executionId(), null, null, execution.status().name(), output);
            }
            lifecycle = saveOwned(lifecycle.progress(
                    HistoricalReconstructionLifecyclePhase.ANALYSIS_COMPLETED, null, null, clock.instant()));
            UUID executionId = execution.executionId();
            String evidenceSelection = execution.provenance().historicalEvidenceSelection();
            FeatureSnapshot snapshot = runPhase(phases, () -> projections.project(executionId),
                    deadline, "feature projection");
            ensureWithinDeadline(deadline, "feature projection");
            lifecycle = saveOwned(lifecycle.progress(
                    HistoricalReconstructionLifecyclePhase.FEATURE_PROJECTION_COMPLETED,
                    snapshot.fingerprint(), null, clock.instant()));
            ObservedMarketOutcome outcome = runPhase(phases, () -> outcomes.evaluate(new MarketOutcomeEvaluationRequest(
                    marketId, cutoff, new OutcomeHorizon(Duration.ofHours(1)), executionId,
                    evidenceSelection)), deadline, "outcome evaluation");
            ensureWithinDeadline(deadline, "outcome evaluation");
            if (outcome.status() != ObservedMarketOutcomeStatus.AVAILABLE) {
                terminalStatus = "OUTCOME_" + outcome.status().name();
                lifecycle = recordFailure(lifecycle, HistoricalReconstructionLifecycleStatus.PARTIAL,
                        terminalStatus, "Canonical outcome is not available");
                return new Result(execution.executionId(), snapshot.fingerprint(), outcome.outcomeFingerprint(),
                        terminalStatus, output);
            }
            lifecycle = saveOwned(lifecycle.progress(
                    HistoricalReconstructionLifecyclePhase.OUTCOME_EVALUATION_COMPLETED,
                    null, outcome.outcomeFingerprint(), clock.instant()));
            ResearchPopulationSpec population = new ResearchPopulationSpec(
                    // PostgreSQL timestamp(6) truncates nanosecond-only bounds.
                    "historical-reconstruction-v1", Set.of(marketId), cutoff, cutoff.plusNanos(1_000),
                    PROJECTION, EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                    outcome.outcomeDefinitionVersion(), Set.of(new OutcomeHorizon(Duration.ofHours(1))),
                    ResearchSamplingCadence.ONE_PER_FEATURE_SNAPSHOT, Set.of(),
                    java.util.Map.of(new ResearchFeatureSnapshotKey(marketId, cutoff), snapshot.fingerprint()),
                    ResearchFeatureRequirements.q1(),
                    java.util.Map.of(new ResearchOutcomeKey(marketId, cutoff, new OutcomeHorizon(Duration.ofHours(1))),
                    outcome.outcomeFingerprint()));
            ResearchDatasetExportService.ExportResult export = runPhase(phases,
                    () -> exports.export(population, output), deadline, "dataset export");
            ensureWithinDeadline(deadline, "dataset export");
            if (export.eligibleCount() != 1) {
                recordFailure(lifecycle, HistoricalReconstructionLifecycleStatus.PARTIAL,
                        "EXPORT_ELIGIBILITY_COUNT", "Expected exactly one eligible sample, got "
                                + export.eligibleCount());
                throw new IllegalStateException(
                        "Historical reconstruction requires exactly one eligible dataset sample, got "
                                + export.eligibleCount());
            }
            terminalStatus = "EXPORTED";
            saveOwned(lifecycle.success(snapshot.fingerprint(), outcome.outcomeFingerprint(), clock.instant()));
            return new Result(execution.executionId(), snapshot.fingerprint(), outcome.outcomeFingerprint(), terminalStatus, output);
        } catch (RuntimeException failure) {
            if (execution != null && !execution.status().isTerminal()) {
                cancelIfActive(execution.executionId());
            }
            if (lifecycle != null && lifecycle.status() == HistoricalReconstructionLifecycleStatus.RUNNING) {
                HistoricalReconstructionLifecycleStatus status = lifecycle.phase()
                        == HistoricalReconstructionLifecyclePhase.FEATURE_PROJECTION_COMPLETED
                        || lifecycle.phase() == HistoricalReconstructionLifecyclePhase.OUTCOME_EVALUATION_COMPLETED
                        ? HistoricalReconstructionLifecycleStatus.PARTIAL
                        : HistoricalReconstructionLifecycleStatus.FAILED;
                terminalStatus = status.name();
                recordFailure(lifecycle, status, failureCode(terminalStatus), failure.getMessage());
            } else if (lifecycle == null && idempotencyKey != null) {
                AnalysisExecution existing = executionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
                if (existing != null
                        && existing.provenance().executionOrigin()
                        == com.hope.trading.market_intelligence.domain.execution.ExecutionOrigin.HISTORICAL_RECONSTRUCTION
                        && existing.status() == AnalysisExecutionStatus.FAILED) {
                    HistoricalReconstructionLifecycle started = lifecycles.save(
                            HistoricalReconstructionLifecycle.started(existing.executionId(), output.toString(), clock.instant()));
                    terminalStatus = HistoricalReconstructionLifecycleStatus.FAILED.name();
                    recordFailure(started, HistoricalReconstructionLifecycleStatus.FAILED,
                            failureCode(terminalStatus), failure.getMessage());
                }
            }
            throw failure;
        } finally {
            phases.shutdownNow();
            log.info("historical_reconstruction_audit phase=FINAL operator={} approvalReference={} marketId={} cutoff={} runId={} executionId={} terminalStatus={} outputDirectory={}",
                    operatorPrincipal, approvalReference, marketId, cutoff, runId,
                    execution == null ? null : execution.executionId(), terminalStatus, output);
        }
    }

    private Result existingResult(HistoricalReconstructionLifecycle lifecycle, Path requestedOutput) {
        String status = lifecycle.status() == HistoricalReconstructionLifecycleStatus.SUCCEEDED
                ? "EXPORTED" : lifecycle.status().name();
        Path output = lifecycle.outputDirectory() == null ? requestedOutput : Path.of(lifecycle.outputDirectory());
        return new Result(lifecycle.executionId(), lifecycle.snapshotFingerprint(),
                lifecycle.outcomeFingerprint(), status, output);
    }

    private HistoricalReconstructionLifecycle recordFailure(
            HistoricalReconstructionLifecycle lifecycle,
            HistoricalReconstructionLifecycleStatus status,
            String code,
            String reason
    ) {
        try {
            return saveOwned(lifecycle.failure(status, code, reason, clock.instant()));
        } catch (RuntimeException persistenceFailure) {
            log.error("historical_reconstruction_lifecycle_failure_persistence_failed executionId={} phase={} code={}",
                    lifecycle.executionId(), lifecycle.phase(), code, persistenceFailure);
            throw persistenceFailure;
        }
    }

    private HistoricalReconstructionLifecycle saveOwned(HistoricalReconstructionLifecycle next) {
        if (!lifecycles.saveOwned(next, next.leaseId())) {
            throw new IllegalStateException("Historical reconstruction lifecycle lease was lost");
        }
        return next;
    }

    private String failureCode(String terminalStatus) {
        return terminalStatus == null || terminalStatus.isBlank() ? "RECONSTRUCTION_FAILED" : terminalStatus;
    }

    private AnalysisExecution awaitTerminal(UUID executionId, long deadline) {
        while (System.nanoTime() < deadline) {
            AnalysisExecution current = executionRepository.findById(executionId)
                    .orElseThrow(() -> new IllegalStateException("Reconstruction execution disappeared"));
            if (current.status().isTerminal()) return current;
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                cancelIfActive(executionId);
                throw new IllegalStateException("Historical reconstruction interrupted", interrupted);
            }
        }
        cancelIfActive(executionId);
        AnalysisExecution cancelled = executionRepository.findById(executionId).orElseThrow();
        if (!cancelled.status().isTerminal()) {
            throw new IllegalStateException("Historical reconstruction cancellation did not reach a terminal state");
        }
        return cancelled;
    }

    private void cancelIfActive(UUID executionId) {
        AnalysisExecution current = executionRepository.findById(executionId).orElseThrow();
        if (!current.status().isTerminal()) {
            executions.cancel(executionId);
        }
    }

    private void ensureWithinDeadline(long deadline, String phase) {
        if (System.nanoTime() >= deadline) {
            throw new IllegalStateException("Historical reconstruction exceeded five-minute timeout during " + phase);
        }
    }

    private <T> T runPhase(ExecutorService executor, Callable<T> phase, long deadline, String name) {
        ensureWithinDeadline(deadline, name);
        Future<T> future = executor.submit(phase);
        long remaining = deadline - System.nanoTime();
        try {
            return future.get(Math.max(1, remaining), TimeUnit.NANOSECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            throw new IllegalStateException("Historical reconstruction timed out during " + name, timeout);
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Historical reconstruction interrupted during " + name, interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Historical reconstruction failed during " + name, cause);
        }
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
