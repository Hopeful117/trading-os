package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.application.port.AnalysisExecutionDispatcher;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.pipeline.ProductionIntelligencePipeline;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.IntelligenceExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Local asynchronous transport for V1. The dispatcher boundary allows a
 * durable queue or remote coordinator to replace it without changing domain
 * contracts.
 */
@Component
public class LocalAnalysisExecutionDispatcher implements AnalysisExecutionDispatcher {
    private final AnalysisExecutionRepository repository;
    private final CapabilityAnalysisCoordinator coordinator;
    private final ExecutorService executor;
    private final ProductionIntelligencePipeline pipeline;
    private final ConcurrentMap<UUID, Future<?>> tasks = new ConcurrentHashMap<>();

    public LocalAnalysisExecutionDispatcher(
            AnalysisExecutionRepository repository,
            CapabilityAnalysisCoordinator coordinator,
            ProductionIntelligencePipeline pipeline,
            @Qualifier("analysisExecutionDispatcherExecutor") ExecutorService executionDispatcher
    ) {
        this.repository = repository;
        this.coordinator = coordinator;
        this.pipeline = pipeline;
        this.executor = executionDispatcher;
    }

    @Override
    public void dispatch(UUID executionId, IntelligenceAnalysisRequest request) {
        tasks.put(executionId, executor.submit(() -> execute(executionId, request)));
    }

    @Override
    public void cancel(UUID executionId) {
        Future<?> task = tasks.remove(executionId);
        if (task != null) {
            coordinator.cancel(executionId);
            task.cancel(true);
        }
    }

    private void execute(UUID executionId, IntelligenceAnalysisRequest request) {
        try {
            if (!repository.transitionStatus(
                    executionId,
                    AnalysisExecutionStatus.ACCEPTED,
                    AnalysisExecutionStatus.CONTEXT_BUILDING,
                    Instant.now()
            )) {
                return;
            }
            transition(executionId, AnalysisExecutionStatus.RUNNING);
            ConsolidatedIntelligence result = coordinator.analyze(executionId, request);
            repository.findById(executionId)
                    .filter(execution -> !execution.status().isTerminal())
                    .map(execution -> execution.complete(
                            result, quality(result.status()), Instant.now()
                    ))
                    .ifPresent(repository::save);
            if (result.status() != IntelligenceExecutionStatus.FAILED) {
                pipeline.process(executionId, request.marketId(), request.mode());
            }
        } catch (CancellationException ignored) {
            // Cancellation state is owned by AnalysisExecutionService.
        } catch (AnalysisContextUnavailableException exception) {
            repository.findById(executionId)
                    .filter(execution -> !execution.status().isTerminal())
                    .map(execution -> execution.transitionTo(
                            AnalysisExecutionStatus.FAILED, Instant.now()
                    ))
                    .ifPresent(repository::save);
        } catch (RuntimeException exception) {
            repository.findById(executionId)
                    .filter(execution -> !execution.status().isTerminal())
                    .map(execution -> execution.transitionTo(
                            AnalysisExecutionStatus.FAILED, Instant.now()
                    ))
                    .ifPresent(repository::save);
        } finally {
            tasks.remove(executionId);
        }
    }

    private void transition(UUID executionId, AnalysisExecutionStatus status) {
        AnalysisExecution current = repository.findById(executionId)
                .orElseThrow(() -> new AnalysisExecutionNotFoundException(executionId));
        if (current.status().isTerminal()) {
            throw new CancellationException("Execution already terminal");
        }
        repository.save(current.transitionTo(status, Instant.now()));
    }

    private AnalysisResultQuality quality(IntelligenceExecutionStatus status) {
        return switch (status) {
            case COMPLETE -> AnalysisResultQuality.COMPLETE;
            case PARTIAL -> AnalysisResultQuality.PARTIAL;
            case DEGRADED, FAILED -> AnalysisResultQuality.DEGRADED;
        };
    }
}
