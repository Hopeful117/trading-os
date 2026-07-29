package com.hope.trading.market_intelligence.domain.capability;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class CapabilityExecutionTest {
    private final Capability capability = CapabilityTestFixtures.capability(
            "test", List.of(), List.of(), RetryPolicy.disabled(),
            context -> CapabilityResult.noOpportunity(List.of("No opportunity")));

    @Test
    void supportsValidLifecycleAndNegativeBusinessResultCompletes() {
        CapabilityExecution execution = CapabilityExecution.created(
                        UUID.randomUUID(), capability.metadata(), CapabilityTestFixtures.NOW)
                .transitionTo(CapabilityExecutionState.READY, CapabilityTestFixtures.NOW)
                .transitionTo(CapabilityExecutionState.RUNNING, CapabilityTestFixtures.NOW)
                .complete(CapabilityResult.noOpportunity(List.of()), CapabilityTestFixtures.NOW);

        assertThat(execution.state()).isEqualTo(CapabilityExecutionState.COMPLETED);
        assertThat(execution.result()).isPresent();
    }

    @Test
    void rejectsInvalidAndTerminalTransitions() {
        CapabilityExecution created = CapabilityExecution.created(
                UUID.randomUUID(), capability.metadata(), CapabilityTestFixtures.NOW);
        assertThatThrownBy(() -> created.transitionTo(
                CapabilityExecutionState.COMPLETED, CapabilityTestFixtures.NOW))
                .isInstanceOf(IllegalCapabilityExecutionTransitionException.class);
        CapabilityExecution cancelled = created.transitionTo(
                CapabilityExecutionState.CANCELLED, CapabilityTestFixtures.NOW);
        assertThatThrownBy(() -> cancelled.transitionTo(
                CapabilityExecutionState.READY, CapabilityTestFixtures.NOW))
                .isInstanceOf(IllegalCapabilityExecutionTransitionException.class);
    }

    @Test
    void retryCreatesNewExecutionAndPreservesAttemptLineage() {
        CapabilityFailure failure = new CapabilityFailure(
                "NETWORK", "NETWORK", "down", true, null, null,
                CapabilityTestFixtures.NOW, Map.of());
        CapabilityExecution failed = CapabilityExecution.created(
                        UUID.randomUUID(), capability.metadata(), CapabilityTestFixtures.NOW)
                .transitionTo(CapabilityExecutionState.READY, CapabilityTestFixtures.NOW)
                .transitionTo(CapabilityExecutionState.RUNNING, CapabilityTestFixtures.NOW)
                .fail(failure, CapabilityTestFixtures.NOW);
        CapabilityExecution retry = CapabilityExecution.retryFrom(
                failed, CapabilityTestFixtures.NOW.plusSeconds(1));

        assertThat(retry.id()).isNotEqualTo(failed.id());
        assertThat(retry.executionGroupId()).isEqualTo(failed.executionGroupId());
        assertThat(retry.attemptNumber()).isEqualTo(2);
        assertThat(retry.previousAttemptId()).contains(failed.id());
    }
}
