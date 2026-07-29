package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.application.planning.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.planning.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionEngineTest {
    private final Clock clock = Clock.fixed(CapabilityTestFixtures.NOW, ZoneOffset.UTC);

    @Test
    void executesSimpleDagAndUnblocksConsumerWithProducedArtifact() {
        AtomicBoolean consumerReceived = new AtomicBoolean();
        Capability producer = cap("producer", List.of(), List.of(
                CapabilityTestFixtures.produces(
                        CapabilityTestFixtures.INTERMEDIATE, CapabilityTestFixtures.V1)),
                context -> CapabilityTestFixtures.result(
                        CapabilityTestFixtures.INTERMEDIATE, CapabilityTestFixtures.V1));
        ArtifactRequirement requirement = CapabilityTestFixtures.requirement(
                CapabilityTestFixtures.INTERMEDIATE, CapabilityTestFixtures.V1);
        Capability consumer = cap("consumer", List.of(requirement), List.of(), context -> {
            consumerReceived.set(context.resolvedArtifacts().containsKey(requirement));
            return CapabilityResult.noOpportunity(List.of("negative business result"));
        });

        ExecutionSummary summary = execute(plan(producer, consumer), new ExecutionControl(), 2);

        assertThat(summary.state()).isEqualTo(ExecutionEngineState.COMPLETED);
        assertThat(consumerReceived).isTrue();
        assertThat(summary.attempts())
                .extracting(CapabilityExecution::state)
                .containsOnly(CapabilityExecutionState.COMPLETED);
    }

    @Test
    void runsIndependentBranchesConcurrently() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicBoolean parallel = new AtomicBoolean(true);
        java.util.function.Function<CapabilityContext, CapabilityResult> work = context -> {
            bothStarted.countDown();
            try {
                if (!bothStarted.await(1, TimeUnit.SECONDS)) parallel.set(false);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return CapabilityResult.noOpportunity(List.of());
        };

        execute(plan(
                cap("a", List.of(), List.of(), work),
                cap("b", List.of(), List.of(), work)), new ExecutionControl(), 2);

        assertThat(parallel).isTrue();
    }

    @Test
    void localFailureDoesNotStopIndependentBranchAndSkipsMandatoryDependent() {
        Capability failing = cap("failing", List.of(), List.of(
                CapabilityTestFixtures.produces(
                        CapabilityTestFixtures.INTERMEDIATE, CapabilityTestFixtures.V1)),
                context -> { throw new DeterministicFailure(); });
        Capability independent = cap("independent", List.of(), List.of(),
                context -> CapabilityResult.noOpportunity(List.of()));
        Capability dependent = cap("dependent", List.of(
                CapabilityTestFixtures.requirement(
                        CapabilityTestFixtures.INTERMEDIATE, CapabilityTestFixtures.V1)),
                List.of(), context -> CapabilityResult.noOpportunity(List.of()));

        ExecutionSummary summary = execute(
                plan(failing, independent, dependent), new ExecutionControl(), 2);

        assertThat(states(summary, "failing")).contains(CapabilityExecutionState.FAILED);
        assertThat(states(summary, "independent")).contains(CapabilityExecutionState.COMPLETED);
        assertThat(summary.attempts()).anyMatch(execution ->
                execution.capabilityId().value().equals("dependent")
                        && execution.state() == CapabilityExecutionState.SKIPPED
                        && execution.skipReason().orElseThrow()
                        == SkipReason.UNSATISFIED_REQUIREMENT);
    }

    @Test
    void partialConsumerRunsWithStructuredMissingRequirement() {
        ArtifactRequirement partial = new ArtifactRequirement(
                CapabilityTestFixtures.INTERMEDIATE, CapabilityTestFixtures.V1,
                VersionCompatibilityMode.EXACT, true, ArtifactCardinality.ONE, true);
        Capability failing = cap("failing", List.of(), List.of(
                CapabilityTestFixtures.produces(
                        CapabilityTestFixtures.INTERMEDIATE, CapabilityTestFixtures.V1)),
                context -> { throw new DeterministicFailure(); });
        AtomicBoolean sawMissing = new AtomicBoolean();
        Capability consumer = cap("partial", List.of(partial), List.of(), context -> {
            sawMissing.set(context.missingRequirements().contains(partial));
            return new CapabilityResult(
                    List.of(), List.of(), Map.of(), List.of(),
                    CapabilityCompleteness.PARTIAL);
        });

        ExecutionSummary summary = execute(
                plan(failing, consumer), new ExecutionControl(), 2);

        assertThat(sawMissing).isTrue();
        assertThat(states(summary, "partial")).contains(CapabilityExecutionState.COMPLETED);
    }

    @Test
    void retriesOnlyRetryableFailuresAndPreservesLineageAndMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();
        RetryPolicy retry = new RetryPolicy(
                true, 2, BackoffStrategy.FIXED, Duration.ZERO,
                Duration.ZERO, Set.of(TransientFailure.class.getSimpleName()));
        Capability capability = CapabilityTestFixtures.capability(
                "retry", List.of(), List.of(), retry, context -> {
                    if (calls.getAndIncrement() == 0) throw new TransientFailure();
                    return CapabilityResult.noOpportunity(List.of());
                });

        ExecutionSummary summary = execute(plan(capability), new ExecutionControl(), 1);
        List<CapabilityExecution> attempts = summary.attempts().stream()
                .filter(item -> item.capabilityId().value().equals("retry"))
                .sorted(Comparator.comparingInt(CapabilityExecution::attemptNumber)).toList();

        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(1).executionGroupId())
                .isEqualTo(attempts.get(0).executionGroupId());
        assertThat(attempts.get(1).previousAttemptId()).contains(attempts.get(0).id());
        assertThat(attempts.get(1).state()).isEqualTo(CapabilityExecutionState.COMPLETED);
    }

    @Test
    void doesNotRetryNonRetryableFailureAndStopsAtMaximumAttempts() {
        AtomicInteger nonRetryableCalls = new AtomicInteger();
        RetryPolicy policy = new RetryPolicy(
                true, 2, BackoffStrategy.FIXED, Duration.ZERO,
                Duration.ZERO, Set.of(TransientFailure.class.getSimpleName()));
        Capability nonRetryable = CapabilityTestFixtures.capability(
                "non-retryable", List.of(), List.of(), policy, context -> {
                    nonRetryableCalls.incrementAndGet();
                    throw new DeterministicFailure();
                });
        ExecutionSummary first = execute(
                plan(nonRetryable), new ExecutionControl(), 1);

        AtomicInteger retryableCalls = new AtomicInteger();
        Capability alwaysFailing = CapabilityTestFixtures.capability(
                "max-attempts", List.of(), List.of(), policy, context -> {
                    retryableCalls.incrementAndGet();
                    throw new TransientFailure();
                });
        ExecutionSummary second = execute(
                plan(alwaysFailing), new ExecutionControl(), 1);

        assertThat(nonRetryableCalls).hasValue(1);
        assertThat(first.attempts()).hasSize(1);
        assertThat(retryableCalls).hasValue(2);
        assertThat(second.attempts()).hasSize(2);
        assertThat(second.attempts().getLast().state())
                .isEqualTo(CapabilityExecutionState.FAILED);
    }

    @Test
    void cancellationBeforeStartPreventsAllLaunches() {
        AtomicInteger calls = new AtomicInteger();
        Capability capability = cap("never", List.of(), List.of(), context -> {
            calls.incrementAndGet();
            return CapabilityResult.noOpportunity(List.of());
        });
        ExecutionControl control = new ExecutionControl();
        control.requestCancellation(cancellation());

        ExecutionSummary summary = execute(plan(capability), control, 1);

        assertThat(calls).hasValue(0);
        assertThat(summary.state()).isEqualTo(ExecutionEngineState.CANCELLED);
        assertThat(control.stateHistory()).containsExactly(
                ExecutionEngineState.RUNNING,
                ExecutionEngineState.CANCEL_REQUESTED,
                ExecutionEngineState.CANCELLED);
        assertThat(summary.attempts())
                .extracting(CapabilityExecution::state)
                .containsOnly(CapabilityExecutionState.CANCELLED);
    }

    @Test
    void cancellationDuringRunRejectsLateResultAndCreatesNoRetry() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RetryPolicy retry = new RetryPolicy(
                true, 3, BackoffStrategy.FIXED, Duration.ZERO,
                Duration.ZERO, Set.of(TransientFailure.class.getSimpleName()));
        Capability capability = CapabilityTestFixtures.capability(
                "late", List.of(), List.of(), retry, context -> {
                    started.countDown();
                    try { release.await(); } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return CapabilityResult.noOpportunity(List.of());
                });
        ExecutionControl control = new ExecutionControl();
        ExecutorService runner = Executors.newSingleThreadExecutor();
        Future<ExecutionSummary> future = runner.submit(
                () -> execute(plan(capability), control, 1));
        started.await(1, TimeUnit.SECONDS);
        control.requestCancellation(cancellation());
        release.countDown();

        ExecutionSummary summary = future.get(2, TimeUnit.SECONDS);
        runner.shutdownNow();

        assertThat(summary.state()).isEqualTo(ExecutionEngineState.CANCELLED);
        assertThat(summary.rejectedLateResults()).isEqualTo(1);
        assertThat(summary.attempts()).hasSize(1);
        assertThat(summary.attempts().getFirst().state())
                .isEqualTo(CapabilityExecutionState.CANCELLED);
    }

    private ExecutionSummary execute(
            ExecutionPlan plan, ExecutionControl control, int parallelism) {
        try (LocalCapabilityExecutor executor = new LocalCapabilityExecutor(parallelism)) {
            return new ExecutionEngine(
                    executor, new InMemoryCapabilityExecutionRepository(),
                    new InMemoryCapabilityArtifactPersistenceAdapter(),
                    new BackoffCalculator(), (duration, token) ->
                    token.throwIfCancellationRequested(), clock).execute(plan, control);
        }
    }
    private ExecutionPlan plan(Capability... capabilities) {
        CapabilityRegistry registry = new CapabilityRegistry();
        Arrays.stream(capabilities).forEach(registry::register);
        return new ExecutionPlanner(
                registry, new ArtifactAdapterRegistry(), clock).plan(
                new PlanningRequest(UUID.randomUUID(), Set.of(), Set.of(), Set.of()));
    }
    private Capability cap(
            String id, List<ArtifactRequirement> requirements,
            List<ProducedContribution> outputs,
            java.util.function.Function<CapabilityContext, CapabilityResult> behavior) {
        return CapabilityTestFixtures.capability(
                id, requirements, outputs, RetryPolicy.disabled(), behavior);
    }
    private List<CapabilityExecutionState> states(ExecutionSummary summary, String id) {
        return summary.attempts().stream()
                .filter(execution -> execution.capabilityId().value().equals(id))
                .map(CapabilityExecution::state).toList();
    }
    private CancellationRequest cancellation() {
        return new CancellationRequest(
                CapabilityTestFixtures.NOW, "test", "stop",
                CancellationSource.USER, "correlation");
    }
    private static class DeterministicFailure extends RuntimeException {}
    private static class TransientFailure extends RuntimeException {}
}
