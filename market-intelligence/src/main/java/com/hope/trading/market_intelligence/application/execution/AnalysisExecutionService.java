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
import java.util.List;
import java.util.UUID;

/**
 * Application boundary owning execution creation, idempotence, lookup and
 * cancellation. AI providers never create or mutate this aggregate.
 */
@Service
public class AnalysisExecutionService {
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
        AnalysisExecution execution = registerInternal(
                request, idempotencyKey, requestId, traceId, true, selectionFingerprint);
        if (claimForDispatch(execution.executionId())) {
            dispatchRegistered(execution.executionId());
        }
        return execution;
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
        String fingerprint = request.marketId() + "|" + request.evaluationCutoff()
                + "|feature-projection-v1|PT1H|STRICT_OBSERVED|"
                + String.join(",", HistoricalReconstructionPolicy.ALLOWED_CAPABILITIES)
                + "|selection=" + (selectionFingerprint == null ? "preflight-unavailable" : selectionFingerprint);
        return new AnalysisExecutionProvenance(
                request.marketId(), request.mode(), request.objective(), "v1",
                request.accountId(), request.scanId(), request.scanMarketId(),
                request.evaluationCutoff(), request.evidencePolicy(),
                ExecutionOrigin.HISTORICAL_RECONSTRUCTION, fingerprint);
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
        ));
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
        AnalysisExecution cancelled = current.transitionTo(
                AnalysisExecutionStatus.CANCELLED, clock.instant()
        );
        repository.save(cancelled);
        dispatcher.cancel(executionId);
        return cancelled;
    }
}
