package com.hope.trading.market_intelligence.domain.execution;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class ExecutionTestFixtures {
    public static AnalysisExecutionPolicy policy() {
        return new AnalysisExecutionPolicy(
                Duration.ofMinutes(2),
                Duration.ofSeconds(10),
                2,
                3,
                new ContextLimits(10, 1_000, 200, 3, ContextClassification.INTERNAL),
                new RetryPolicy(
                        2, Duration.ofSeconds(1), Set.of(RetryClassification.RETRYABLE)
                ),
                Map.of("spread-analysis", CapabilityPriority.MANDATORY),
                new DegradationPolicy(true, true, true, true)
        );
    }

    public static AnalysisExecution requested(Instant now) {
        UUID executionId = UUID.randomUUID();
        return AnalysisExecution.requested(
                executionId,
                new IdempotencyKey("market:passive:window"),
                policy(),
                now,
                List.of("spread-analysis"),
                new AnalysisExecutionProvenance(
                        UUID.randomUUID(), AnalysisExecutionMode.PASSIVE, "", "v1"
                ),
                AnalysisTraceMetadata.empty()
        );
    }

    public static ConsolidatedIntelligence result(
            UUID analysisId,
            UUID marketId,
            Instant now
    ) {
        return new ConsolidatedIntelligence(
                analysisId,
                marketId,
                AnalysisExecutionMode.PASSIVE,
                IntelligenceExecutionStatus.COMPLETE,
                List.of(),
                List.of(),
                List.of(),
                new AnalysisExecutionMetadata(
                        analysisId,
                        AnalysisExecutionMode.PASSIVE,
                        now,
                        now,
                        Duration.ZERO,
                        Duration.ofSeconds(10),
                        List.of()
                )
        );
    }

    private ExecutionTestFixtures() {
    }
}
