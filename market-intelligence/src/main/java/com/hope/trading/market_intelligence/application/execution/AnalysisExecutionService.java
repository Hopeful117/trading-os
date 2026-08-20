package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.application.port.AnalysisExecutionDispatcher;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.strategy.AnalysisExecutionPlan;
import com.hope.trading.market_intelligence.application.strategy.AnalysisStrategyRegistry;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.*;
import com.hope.trading.market_intelligence.domain.security.*;
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
        Instant now = clock.instant();
        return repository.findReusable(idempotencyKey, now).orElseGet(() -> {
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
                    new AnalysisExecutionProvenance(
                            request.marketId(), request.mode(), request.objective(), "v1"
                    ),
                    new AnalysisTraceMetadata(List.of(trace))
            );
            repository.save(execution);
            return execution;
        });
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
                execution.provenance().objective()
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
