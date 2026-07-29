package com.hope.trading.market_intelligence.domain.execution;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class AnalysisExecutionTest {
    private final Instant now = Instant.parse("2026-07-29T10:00:00Z");

    @Test
    void followsTheAcceptedLifecycleAndKeepsAcceptedResultImmutable() {
        AnalysisExecution requested = ExecutionTestFixtures.requested(now);
        AnalysisExecution running = requested
                .transitionTo(AnalysisExecutionStatus.ACCEPTED, now.plusSeconds(1))
                .transitionTo(AnalysisExecutionStatus.CONTEXT_BUILDING, now.plusSeconds(2))
                .transitionTo(AnalysisExecutionStatus.RUNNING, now.plusSeconds(3));
        var result = ExecutionTestFixtures.result(
                running.executionId(), running.provenance().marketId(), now
        );

        AnalysisExecution completed = running.complete(
                result, AnalysisResultQuality.COMPLETE, now.plusSeconds(4)
        );

        assertThat(completed.status()).isEqualTo(AnalysisExecutionStatus.COMPLETED);
        assertThat(completed.status().isTerminal()).isTrue();
        assertThat(completed.result()).containsSame(result);
        assertThat(completed.resultQuality()).contains(AnalysisResultQuality.COMPLETE);
        assertThatThrownBy(() -> completed.transitionTo(
                AnalysisExecutionStatus.RUNNING, now.plusSeconds(5)
        )).isInstanceOf(IllegalExecutionTransitionException.class);
    }

    @Test
    void rejectsIllegalTransition() {
        AnalysisExecution execution = ExecutionTestFixtures.requested(now);

        assertThatThrownBy(() -> execution.transitionTo(
                AnalysisExecutionStatus.COMPLETED, now.plusSeconds(1)
        )).isInstanceOf(IllegalExecutionTransitionException.class);
    }

    @Test
    void cancellationAndExpirationAreTerminal() {
        AnalysisExecution cancelled = ExecutionTestFixtures.requested(now)
                .transitionTo(AnalysisExecutionStatus.CANCELLED, now.plusSeconds(1));
        AnalysisExecution expired = ExecutionTestFixtures.requested(now)
                .transitionTo(AnalysisExecutionStatus.EXPIRED, now.plusSeconds(1));

        assertThat(cancelled.status().isTerminal()).isTrue();
        assertThat(expired.status().isTerminal()).isTrue();
        assertThatThrownBy(() -> cancelled.transitionTo(
                AnalysisExecutionStatus.ACCEPTED, now.plusSeconds(2)
        )).isInstanceOf(IllegalExecutionTransitionException.class);
    }
}
