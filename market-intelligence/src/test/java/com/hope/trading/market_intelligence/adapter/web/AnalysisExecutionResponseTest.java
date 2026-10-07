package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.execution.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisExecutionResponseTest {
    @Test
    void exposesBoundedFailureCode() {
        Instant now = Instant.parse("2026-08-20T12:00:00Z");
        AnalysisExecution execution = AnalysisExecution.requested(
                UUID.randomUUID(),
                new IdempotencyKey("response-test"),
                new AnalysisExecutionPolicy(
                        Duration.ofMinutes(5), Duration.ofSeconds(30), 0, 1,
                        new ContextLimits(10, 10, 10, 5, ContextClassification.PUBLIC),
                        new RetryPolicy(0, Duration.ZERO, java.util.Set.of()),
                        Map.of(), new DegradationPolicy(true, true, true, true)
                ),
                now,
                List.of(),
                new AnalysisExecutionProvenance(UUID.randomUUID(), AnalysisExecutionMode.ACTIVE, "scan", "v1"),
                new AnalysisTraceMetadata(List.of())
        ).transitionTo(AnalysisExecutionStatus.ACCEPTED, now.plusSeconds(1))
                .fail("MARKET_SNAPSHOT_STALE", now.plusSeconds(2));

        assertThat(AnalysisExecutionResponse.from(execution).failureCode())
                .isEqualTo("MARKET_SNAPSHOT_STALE");
    }
}
