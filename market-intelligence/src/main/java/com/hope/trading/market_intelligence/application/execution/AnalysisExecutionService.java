package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.application.port.AnalysisExecutionDispatcher;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.strategy.AnalysisExecutionPlan;
import com.hope.trading.market_intelligence.application.strategy.AnalysisStrategyRegistry;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.*;
import com.hope.trading.market_intelligence.domain.security.*;
import com.hope.trading.market_intelligence.application.research.HistoricalReconstructionPolicy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Application boundary owning execution creation, idempotence, lookup and
 * cancellation. AI providers never create or mutate this aggregate.
 */
@Service
public class AnalysisExecutionService {
    private static final Duration DISPATCH_CLAIM_LEASE = Duration.ofSeconds(5);
    private final AnalysisExecutionRepository repository;
    private final AnalysisExecutionDispatcher dispatcher;
    private final AnalysisStrategyRegistry strategies;
    private final AnalysisExecutionPolicyFactory policyFactory;
    private final Clock clock;

    public AnalysisExecutionService(
            AnalysisExecutionRepository repository,
            AnalysisExecutionDispatcher dispatcher,
            AnalysisStrategyRegistry strategies,
            AnalysisExecutionPolicyFactory policyFactory,
            Clock clock
    ) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.strategies = strategies;
        this.policyFactory = policyFactory;
        this.clock = clock;
    }

    public AnalysisExecution create(
            IntelligenceAnalysisRequest request,
            IdempotencyKey idempotencyKey,
            String requestId,
            String traceId
    ) {
        AnalysisExecution execution = register(request, idempotencyKey, requestId, traceId);
        if (claimForDispatch(execution.executionId())) {
            dispatchRegistered(execution.executionId());
        }
        return execution;
    }

    public AnalysisExecution register(
            IntelligenceAnalysisRequest request,
            IdempotencyKey idempotencyKey,
            String requestId,
            String traceId
    ) {
        if (HistoricalReconstructionPolicy.OBJECTIVE.equals(request.objective())) {
            throw new IllegalArgumentException(
                    "Historical reconstruction must use the controlled operator boundary");
        }
        return registerInternal(request, idempotencyKey, requestId, traceId, false);
    }

    public AnalysisExecution createHistorical(
            IntelligenceAnalysisRequest request,
            IdempotencyKey idempotencyKey,
            String requestId,
            String traceId
    ) {
        return createHistorical(request, idempotencyKey, requestId, traceId, null);
    }

    public AnalysisExecution createHistorical(
            IntelligenceAnalysisRequest request,
            IdempotencyKey idempotencyKey,
            String requestId,
            String traceId,
            String selectionFingerprint
    ) {
        return dispatchHistorical(registerHistorical(
                request, idempotencyKey, requestId, traceId, selectionFingerprint));
    }

    public AnalysisExecution registerHistorical(
            IntelligenceAnalysisRequest request,
            IdempotencyKey idempotencyKey,
            String requestId,
            String traceId,
            String selectionFingerprint
    ) {
        return registerInternal(request, idempotencyKey, requestId, traceId, true, selectionFingerprint);
    }

    public AnalysisExecution dispatchHistorical(AnalysisExecution execution) {
        if (claimForDispatch(execution.executionId())) {
            return dispatchHistoricalOrFail(execution);
        } else if (execution.status() == AnalysisExecutionStatus.ACCEPTED
                && !execution.updatedAt().plus(DISPATCH_CLAIM_LEASE).isAfter(clock.instant())
                && repository.requeueAcceptedForRecovery(
                execution.executionId(), clock.instant().minus(DISPATCH_CLAIM_LEASE), clock.instant())) {
            AnalysisExecution requeued = find(execution.executionId());
            if (claimForDispatch(requeued.executionId())) {
                return dispatchHistoricalOrFail(requeued);
            }
        }
        return find(execution.executionId());
    }

    private AnalysisExecution dispatchHistoricalOrFail(AnalysisExecution execution) {
        try {
            dispatchRegistered(execution.executionId());
            return execution;
        } catch (RuntimeException dispatchFailure) {
            if (repository.failIfStatus(execution.executionId(), AnalysisExecutionStatus.ACCEPTED,
                    "DISPATCH_FAILED", clock.instant())) {
                throw new IllegalStateException("Historical analysis dispatch failed", dispatchFailure);
            }
            AnalysisExecution current = find(execution.executionId());
            if (current.status() != AnalysisExecutionStatus.ACCEPTED) return current;
            throw new IllegalStateException("Historical analysis dispatch state is unavailable", dispatchFailure);
        }
    }

    private AnalysisExecution registerInternal(
            IntelligenceAnalysisRequest request,
            IdempotencyKey idempotencyKey,
            String requestId,
            String traceId,
            boolean historicalBoundary
    ) {
        return registerInternal(request, idempotencyKey, requestId, traceId, historicalBoundary, null);
    }

    private AnalysisExecution registerInternal(
            IntelligenceAnalysisRequest request,
            IdempotencyKey idempotencyKey,
            String requestId,
            String traceId,
            boolean historicalBoundary,
            String selectionFingerprint
    ) {
        if (historicalBoundary != HistoricalReconstructionPolicy.OBJECTIVE.equals(request.objective())) {
            throw new IllegalArgumentException("Controlled historical boundary requires its reconstruction objective");
        }
        if (request.evidencePolicy() == EvidenceAcquisitionPolicy.STRICT_OBSERVED
                && request.evaluationCutoff() == null) {
            throw new IllegalArgumentException(
                    "Strict observed analysis requires an explicit evaluation cutoff");
        }
        Instant now = clock.instant();
        if (historicalBoundary) {
            var existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                AnalysisExecutionProvenance expected = provenance(request, true, selectionFingerprint);
                AnalysisExecution current = existing.get();
                if (!sameHistoricalRequest(current.provenance(), expected)) {
                    throw new IllegalStateException(
                            "Conflicting historical reconstruction request for idempotency key");
                }
                if (!current.isExpiredAt(now)
                        && (current.status() == AnalysisExecutionStatus.COMPLETED
                        || !current.status().isTerminal())) {
                    return current;
                }
                throw new IllegalStateException(
                        "Historical reconstruction idempotency key already has a terminal or expired execution; use a new key");
            }
        }
        var reusable = repository.findReusable(idempotencyKey, now);
        if (reusable.isPresent()) {
            if (historicalBoundary && !java.util.Objects.equals(
                    reusable.get().provenance().reconstructionRequestFingerprint(),
                    provenance(request, true, selectionFingerprint).reconstructionRequestFingerprint())) {
                throw new IllegalStateException(
                        "Conflicting historical reconstruction request for idempotency key");
            }
            return reusable.get();
        }
        AnalysisExecutionPlan plan = strategies.strategy(request.mode()).plan(request);
            ExecutionTrace trace = new ExecutionTrace(
                    request.analysisId(),
                    requestId,
                    traceId,
                    ServiceIdentity.marketIntelligence(),
                    AuthorizedCapability.SUBMIT_ANALYSIS,
                    "v1",
                    now
            );
            AnalysisExecution execution = AnalysisExecution.requested(
                    request.analysisId(),
                    idempotencyKey,
                    policyFactory.from(plan),
                    now,
                    plan.capabilityIds(),
                    provenance(request, historicalBoundary, selectionFingerprint),
                    new AnalysisTraceMetadata(List.of(trace))
            );
            repository.save(execution);
            return execution;
    }

    private boolean sameHistoricalRequest(
            AnalysisExecutionProvenance current,
            AnalysisExecutionProvenance expected
    ) {
        String marker = "|selection=";
        String currentFingerprint = current.reconstructionRequestFingerprint();
        String expectedFingerprint = expected.reconstructionRequestFingerprint();
        int currentMarker = currentFingerprint.indexOf(marker);
        int expectedMarker = expectedFingerprint.indexOf(marker);
        return currentMarker >= 0 && expectedMarker >= 0
                && sameFingerprintPrefix(
                currentFingerprint.substring(0, currentMarker),
                expectedFingerprint.substring(0, expectedMarker))
                && java.util.Objects.equals(
                current.historicalEvidenceSelection(), expected.historicalEvidenceSelection());
    }

    private boolean sameFingerprintPrefix(String current, String expected) {
        String[] currentParts = current.split("\\|", -1);
        String[] expectedParts = expected.split("\\|", -1);
        if (currentParts.length != 6 || expectedParts.length != 6) return false;
        for (int index = 0; index < 5; index++) {
            if (!currentParts[index].equals(expectedParts[index])) return false;
        }
        return java.util.Arrays.stream(currentParts[5].split(","))
                .sorted().toList()
                .equals(java.util.Arrays.stream(expectedParts[5].split(","))
                        .sorted().toList());
    }

    private AnalysisExecutionProvenance provenance(
            IntelligenceAnalysisRequest request,
            boolean historicalBoundary,
            String selectionFingerprint
    ) {
        if (!historicalBoundary) {
            return new AnalysisExecutionProvenance(
                    request.marketId(), request.mode(), request.objective(), "v1",
                    request.accountId(), request.scanId(), request.scanMarketId(),
                     request.evaluationCutoff(), request.evidencePolicy());
        }
        if (request.evidencePolicy() != EvidenceAcquisitionPolicy.STRICT_OBSERVED
                || request.evaluationCutoff() == null) {
            throw new IllegalArgumentException(
                    "Historical reconstruction requires strict observed evidence and a cutoff");
        }
        if (request.historicalEvidenceSelection() == null
                || request.historicalEvidenceSelection().isBlank()) {
            throw new IllegalArgumentException(
                    "Historical reconstruction requires a preflight evidence selection");
        }
        String fingerprint = request.marketId() + "|" + request.evaluationCutoff()
                + "|feature-projection-v1|PT1H|STRICT_OBSERVED|"
                + HistoricalReconstructionPolicy.ALLOWED_CAPABILITIES.stream().sorted()
                .collect(java.util.stream.Collectors.joining(","))
                + "|selection=" + (selectionFingerprint == null ? "preflight-unavailable" : selectionFingerprint);
        return new AnalysisExecutionProvenance(
                request.marketId(), request.mode(), request.objective(), "v1",
                request.accountId(), request.scanId(), request.scanMarketId(),
                 request.evaluationCutoff(), request.evidencePolicy(),
                 ExecutionOrigin.HISTORICAL_RECONSTRUCTION, fingerprint,
                 request.historicalEvidenceSelection());
    }

    public boolean claimForDispatch(UUID executionId) {
        return repository.transitionStatus(
                executionId,
                AnalysisExecutionStatus.REQUESTED,
                AnalysisExecutionStatus.ACCEPTED,
                clock.instant()
        );
    }

    public boolean beginProcessing(UUID executionId) {
        return repository.transitionStatus(
                executionId,
                AnalysisExecutionStatus.ACCEPTED,
                AnalysisExecutionStatus.CONTEXT_BUILDING,
                clock.instant()
        );
    }

    public void dispatchRegistered(UUID executionId) {
        AnalysisExecution execution = find(executionId);
        dispatcher.dispatch(execution.executionId(), new IntelligenceAnalysisRequest(
                execution.executionId(),
                execution.provenance().marketId(),
                execution.provenance().mode(),
                execution.provenance().objective(),
                execution.provenance().accountId(),
                execution.provenance().scanId(),
                execution.provenance().scanMarketId(),
                execution.provenance().evaluationCutoff(),
                execution.provenance().evidencePolicy()
        ).withHistoricalEvidenceSelection(execution.provenance().historicalEvidenceSelection()));
    }

    public AnalysisExecution find(UUID executionId) {
        return repository.findById(executionId)
                .orElseThrow(() -> new AnalysisExecutionNotFoundException(executionId));
    }

    public ConsolidatedIntelligence result(UUID executionId) {
        return find(executionId).result()
                .orElseThrow(() -> new IllegalStateException(
                        "Analysis result is not available: " + executionId
                ));
    }

    public AnalysisExecution cancel(UUID executionId) {
        AnalysisExecution current = find(executionId);
        if (current.status().isTerminal()) return current;
        if (!repository.transitionStatus(
                executionId, current.status(), AnalysisExecutionStatus.CANCELLED, clock.instant())) {
            return find(executionId);
        }
        dispatcher.cancel(executionId);
        return find(executionId);
    }
}
