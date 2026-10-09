package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryAnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.pipeline.ProductionIntelligencePipeline;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LocalAnalysisExecutionDispatcherTest {
    private final Instant now = Instant.parse("2026-08-01T12:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"MARKET_SNAPSHOT_STALE", "MARKET_SNAPSHOT_UNAVAILABLE"})
    void persistsBoundedContextFailureCode(String failureCode) throws Exception {
        InMemoryAnalysisExecutionRepository repository = new InMemoryAnalysisExecutionRepository();
        CapabilityAnalysisCoordinator coordinator = mock(CapabilityAnalysisCoordinator.class);
        ProductionIntelligencePipeline pipeline = mock(ProductionIntelligencePipeline.class);
        var executor = Executors.newSingleThreadExecutor();
        UUID executionId = UUID.randomUUID();
        repository.save(acceptedExecution(executionId));
        doThrow(new AnalysisContextUnavailableException(
                failureCode, "Current market snapshot is unavailable"
        )).when(coordinator).analyze(any(), any());

        try {
            LocalAnalysisExecutionDispatcher dispatcher = new LocalAnalysisExecutionDispatcher(
                    repository, coordinator, pipeline, executor
            );
            dispatcher.dispatch(executionId, new IntelligenceAnalysisRequest(
                    executionId, UUID.randomUUID(), AnalysisExecutionMode.ACTIVE, "scan"
            ));

            AnalysisExecution failed = awaitFailure(repository, executionId);
            assertThat(failed.failureCode()).contains(failureCode);
            verifyNoInteractions(pipeline);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private AnalysisExecution awaitFailure(
            InMemoryAnalysisExecutionRepository repository, UUID executionId) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            AnalysisExecution execution = repository.findById(executionId).orElseThrow();
            if (execution.status() == AnalysisExecutionStatus.FAILED) {
                return execution;
            }
            Thread.sleep(10);
        }
        return repository.findById(executionId).orElseThrow();
    }

    private AnalysisExecution acceptedExecution(UUID executionId) {
        AnalysisExecution requested = AnalysisExecution.requested(
                executionId,
                new IdempotencyKey("dispatcher-test"),
                new AnalysisExecutionPolicy(
                        Duration.ofMinutes(5), Duration.ofSeconds(30), 0, 1,
                        new ContextLimits(10, 10, 10, 5,
                                com.hope.trading.market_intelligence.domain.context.ContextClassification.PUBLIC),
                        new RetryPolicy(0, Duration.ZERO, java.util.Set.of()),
                        Map.of(), new DegradationPolicy(true, true, true, true)
                ),
                now,
                List.of(),
                new AnalysisExecutionProvenance(UUID.randomUUID(), AnalysisExecutionMode.ACTIVE, "scan", "v1"),
                new AnalysisTraceMetadata(List.of())
        );
        return requested.transitionTo(AnalysisExecutionStatus.ACCEPTED, now.plusSeconds(1));
    }
}
