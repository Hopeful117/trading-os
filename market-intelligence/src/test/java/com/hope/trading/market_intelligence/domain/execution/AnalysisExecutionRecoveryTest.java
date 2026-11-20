package com.hope.trading.market_intelligence.domain.execution;

import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisExecutionRecoveryTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    void recoveryIsNonTerminalAndPreservesIdentity() {
        AnalysisExecution requested = execution();
        AnalysisExecution accepted = requested.transitionTo(AnalysisExecutionStatus.ACCEPTED,
                REQUESTED_AT.plusSeconds(1));

        AnalysisExecution recovery = accepted.requireRecovery("AI_TIMEOUT", REQUESTED_AT.plusSeconds(2));

        assertThat(recovery.status()).isEqualTo(AnalysisExecutionStatus.RECOVERY_REQUIRED);
        assertThat(recovery.isExpiredAt(REQUESTED_AT.plusSeconds(2))).isFalse();
        assertThat(recovery.executionId()).isEqualTo(accepted.executionId());
        assertThat(recovery.idempotencyKey()).isEqualTo(accepted.idempotencyKey());
        assertThat(recovery.failureCode()).contains("AI_TIMEOUT");
    }

    @Test
    void recoveryCanBeReconciledToRunningButNotCompletedWithoutAResult() {
        AnalysisExecution recovery = execution()
                .transitionTo(AnalysisExecutionStatus.ACCEPTED, REQUESTED_AT.plusSeconds(1))
                .requireRecovery("AI_STATE_UNKNOWN", REQUESTED_AT.plusSeconds(2));

        assertThat(recovery.reconcileRecovery(AnalysisExecutionStatus.RUNNING,
                REQUESTED_AT.plusSeconds(3)).status()).isEqualTo(AnalysisExecutionStatus.RUNNING);
        assertThatThrownBy(() -> recovery.reconcileRecovery(AnalysisExecutionStatus.COMPLETED,
                REQUESTED_AT.plusSeconds(3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminalExecutionCannotEnterRecovery() {
        AnalysisExecution completed = execution()
                .transitionTo(AnalysisExecutionStatus.ACCEPTED, REQUESTED_AT.plusSeconds(1))
                .transitionTo(AnalysisExecutionStatus.CONTEXT_BUILDING, REQUESTED_AT.plusSeconds(2))
                .transitionTo(AnalysisExecutionStatus.RUNNING, REQUESTED_AT.plusSeconds(3))
                .complete(new com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence(
                                null, null, AnalysisExecutionMode.ACTIVE,
                                com.hope.trading.market_intelligence.domain.IntelligenceExecutionStatus.COMPLETE,
                                List.of(), List.of(), List.of(), null),
                        AnalysisResultQuality.COMPLETE, REQUESTED_AT.plusSeconds(4));

        assertThatThrownBy(() -> completed.requireRecovery("late", REQUESTED_AT.plusSeconds(5)))
                .isInstanceOf(IllegalExecutionTransitionException.class);
    }

    private AnalysisExecution execution() {
        UUID executionId = UUID.randomUUID();
        return AnalysisExecution.requested(executionId, new IdempotencyKey("recovery-test"),
                new AnalysisExecutionPolicy(Duration.ofMinutes(1), Duration.ofSeconds(30), 1, 1,
                        new ContextLimits(8, 1_000, 100, 4, ContextClassification.PUBLIC),
                        new RetryPolicy(1, Duration.ZERO, Set.of(RetryClassification.RETRYABLE)),
                        Map.of(), new DegradationPolicy(true, true, true, true)),
                REQUESTED_AT, List.of("ai"),
                new AnalysisExecutionProvenance(UUID.randomUUID(), AnalysisExecutionMode.ACTIVE,
                        "test", "v1"), AnalysisTraceMetadata.empty());
    }
}
