package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.artifact.StoredArtifact;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.planning.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Executes the immutable plan as supplied. It never selects producers, inserts
 * adapters or changes graph topology.
 */
public class ExecutionEngine {
    private final CapabilityExecutor executor;
    private final CapabilityExecutionRepository executions;
    private final ArtifactPersistencePort artifacts;
    private final BackoffCalculator backoff;
    private final RetryDelay retryDelay;
    private final Clock clock;

    public ExecutionEngine(
            CapabilityExecutor executor, CapabilityExecutionRepository executions,
            ArtifactPersistencePort artifacts, BackoffCalculator backoff,
            RetryDelay retryDelay, Clock clock) {
        this.executor = executor;
        this.executions = executions;
        this.artifacts = artifacts;
        this.backoff = backoff;
        this.retryDelay = retryDelay;
        this.clock = clock;
    }

    public ExecutionSummary execute(ExecutionPlan plan, ExecutionControl control) {
        Map<UUID, CapabilityExecution> current = new HashMap<>();
        Map<UUID, CapabilityResult> acceptedResults = new ConcurrentHashMap<>();
        List<CapabilityExecution> history = new ArrayList<>();
        int[] lateResults = {0};
        for (ExecutionNode node : plan.nodes().values()) {
            CapabilityExecution initial = node.initialExecution().transitionTo(
                    node.incomingDependencies().isEmpty()
                            ? CapabilityExecutionState.READY
                            : CapabilityExecutionState.WAITING_FOR_REQUIREMENTS,
                    clock.instant());
            save(initial, current, history, node.id());
        }

        while (current.values().stream().anyMatch(execution -> !execution.state().isTerminal())) {
            if (control.isCancellationRequested()) {
                cancelRemaining(current, history);
                control.completeCancellation();
                return summary(plan, control, history, acceptedResults.size(), lateResults[0]);
            }

            List<ExecutionNode> ready = new ArrayList<>();
            for (ExecutionNode node : plan.nodes().values()) {
                CapabilityExecution execution = current.get(node.id());
                if (execution.state() == CapabilityExecutionState.WAITING_FOR_REQUIREMENTS
                        && dependenciesTerminal(node, current)) {
                    CapabilityContext context = context(plan, node, execution, acceptedResults, control);
                    if (hasUnsatisfiedMandatory(node, context)) {
                        save(execution.skip(SkipReason.UNSATISFIED_REQUIREMENT, clock.instant()),
                                current, history, node.id());
                    } else {
                        CapabilityExecution next = execution.transitionTo(
                                CapabilityExecutionState.READY, clock.instant());
                        save(next, current, history, node.id());
                        ready.add(node);
                    }
                } else if (execution.state() == CapabilityExecutionState.READY) {
                    ready.add(node);
                }
            }
            if (ready.isEmpty()) break;

            Map<UUID, RunningInvocation> running = new LinkedHashMap<>();
            for (ExecutionNode node : ready) {
                if (control.isCancellationRequested()) break;
                CapabilityExecution execution = current.get(node.id()).transitionTo(
                        CapabilityExecutionState.RUNNING, clock.instant());
                save(execution, current, history, node.id());
                CapabilityContext context = context(
                        plan, node, execution, acceptedResults, control);
                running.put(node.id(), new RunningInvocation(
                        node, execution, executor.submit(node.capability(), context)));
            }
            if (control.isCancellationRequested()) {
                running.values().forEach(invocation -> invocation.handle().cancel());
            }
            for (RunningInvocation invocation : running.values()) {
                CapabilityExecution outcome = finishInvocation(
                        plan, invocation, control, acceptedResults, history, lateResults);
                current.put(invocation.node().id(), outcome);
            }
        }
        if (control.isCancellationRequested()) control.completeCancellation();
        else control.completeNormally();
        return summary(plan, control, history, acceptedResults.size(), lateResults[0]);
    }

    private CapabilityExecution finishInvocation(
            ExecutionPlan plan, RunningInvocation invocation, ExecutionControl control,
            Map<UUID, CapabilityResult> acceptedResults,
            List<CapabilityExecution> history, int[] lateResults) {
        CapabilityExecution attempt = invocation.execution();
        CapabilityMetadata metadata = invocation.node().capability().metadata();
        CapabilityExecutionHandle handle = invocation.handle();
        while (true) {
            try {
                CapabilityResult result = handle.await(metadata.timeout());
                if (control.isCancellationRequested()) {
                    lateResults[0]++;
                    return persist(attempt.transitionTo(
                            CapabilityExecutionState.CANCELLED, clock.instant()), history);
                }
                validateResult(metadata, result);
                CapabilityExecution completed = persist(
                        attempt.complete(result, clock.instant()), history);
                result.artifacts().forEach(artifact ->
                        artifacts.save(plan.analysisExecutionId(), artifact));
                acceptedResults.put(invocation.node().id(), result);
                return completed;
            } catch (TimeoutException exception) {
                handle.cancel();
                if (control.isCancellationRequested()) {
                    lateResults[0]++;
                    return persist(attempt.transitionTo(
                            CapabilityExecutionState.CANCELLED, clock.instant()), history);
                }
                attempt = persist(attempt.transitionTo(
                        CapabilityExecutionState.TIMED_OUT, clock.instant()), history);
                CapabilityFailure failure = failure("TIMEOUT", "CAPABILITY_TIMEOUT",
                        true, attempt.id(), exception);
                CapabilityExecution retry = retry(metadata, attempt, failure, control, history);
                if (retry == null) return attempt;
                attempt = retry;
                handle = executor.submit(invocation.node().capability(),
                        context(plan, invocation.node(), attempt,
                                acceptedResults, control));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                if (control.isCancellationRequested()) {
                    lateResults[0]++;
                    return persist(attempt.transitionTo(
                            CapabilityExecutionState.CANCELLED, clock.instant()), history);
                }
            } catch (ExecutionException | RuntimeException exception) {
                if (control.isCancellationRequested()) {
                    lateResults[0]++;
                    return persist(attempt.transitionTo(
                            CapabilityExecutionState.CANCELLED, clock.instant()), history);
                }
                Throwable cause = exception instanceof ExecutionException
                        && exception.getCause() != null ? exception.getCause() : exception;
                boolean retryable = metadata.retryPolicy().retryableFailureTypes()
                        .contains(cause.getClass().getSimpleName());
                CapabilityFailure failure = failure(
                        cause.getClass().getSimpleName(), "CAPABILITY_EXECUTION_FAILED",
                        retryable, attempt.id(), cause);
                attempt = persist(attempt.fail(failure, clock.instant()), history);
                CapabilityExecution retry = retry(metadata, attempt, failure, control, history);
                if (retry == null) return attempt;
                attempt = retry;
                handle = executor.submit(invocation.node().capability(),
                        context(plan, invocation.node(), attempt,
                                acceptedResults, control));
            }
        }
    }

    private CapabilityExecution retry(
            CapabilityMetadata metadata, CapabilityExecution previous,
            CapabilityFailure failure, ExecutionControl control,
            List<CapabilityExecution> history) {
        RetryPolicy policy = metadata.retryPolicy();
        if (control.isCancellationRequested() || !policy.enabled() || !failure.retryable()
                || previous.attemptNumber() >= policy.maxAttempts()) return null;
        try {
            retryDelay.await(backoff.delay(policy, previous.attemptNumber()), control);
        } catch (InterruptedException | CapabilityCancelledException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
        if (control.isCancellationRequested()) return null;
        CapabilityExecution retry = CapabilityExecution.retryFrom(previous, clock.instant())
                .transitionTo(CapabilityExecutionState.READY, clock.instant())
                .transitionTo(CapabilityExecutionState.RUNNING, clock.instant());
        return persist(retry, history);
    }

    private CapabilityContext context(
            ExecutionPlan plan, ExecutionNode node, CapabilityExecution execution,
            Map<UUID, CapabilityResult> acceptedResults, ExecutionControl control) {
        Map<ArtifactRequirement, List<StoredArtifact>> resolved = new HashMap<>();
        Set<ArtifactRequirement> missing = new HashSet<>();
        for (ArtifactRequirement requirement : node.requirements()) {
            List<StoredArtifact> values = new ArrayList<>();
            values.addAll(initialArtifacts(plan, node, requirement));
            for (ExecutionPlan.PlanEdge edge : plan.edges()) {
                if (!edge.to().equals(node.id()) || !edge.requirement().equals(requirement)) continue;
                CapabilityResult result = acceptedResults.get(edge.from());
                if (result == null) continue;
                for (ProducedArtifact produced : result.artifacts()) {
                    if (!produced.type().equals(requirement.artifactType())) continue;
                    StoredArtifact value = adapter(plan, edge.from(), node.id(), requirement)
                            .map(adapter -> adapter.adapt(produced.artifact()))
                            .orElse(produced.artifact());
                    values.add(value);
                }
            }
            if (values.isEmpty()) missing.add(requirement);
            else resolved.put(requirement, List.copyOf(values));
        }
        List<com.hope.trading.market_intelligence.domain.artifact.ArtifactProvenance> provenance =
                resolved.values().stream().flatMap(Collection::stream)
                        .map(StoredArtifact::provenance).toList();
        return new CapabilityContext(
                plan.analysisExecutionId(), execution.id(), resolved, missing,
                Map.of(), provenance, control);
    }

    private List<StoredArtifact> initialArtifacts(
            ExecutionPlan plan, ExecutionNode node, ArtifactRequirement requirement) {
        Optional<ArtifactAdapter> adapter = adapter(plan, null, node.id(), requirement);
        ArtifactVersion sourceVersion = adapter.map(ArtifactAdapter::sourceVersion)
                .orElse(requirement.expectedVersion());
        return artifacts.find(plan.analysisExecutionId(), requirement.artifactType(), sourceVersion)
                .stream().map(ProducedArtifact::artifact)
                .map(value -> adapter.map(item -> item.adapt(value)).orElse(value)).toList();
    }

    private Optional<ArtifactAdapter> adapter(
            ExecutionPlan plan, UUID producer, UUID consumer, ArtifactRequirement requirement) {
        return plan.adapters().stream()
                .filter(binding -> Objects.equals(binding.producerNodeId(), producer)
                        && binding.consumerNodeId().equals(consumer)
                        && binding.requirement().equals(requirement))
                .map(AdapterBinding::adapter).findFirst();
    }

    private boolean dependenciesTerminal(
            ExecutionNode node, Map<UUID, CapabilityExecution> current) {
        return node.incomingDependencies().stream()
                .map(current::get).allMatch(execution -> execution.state().isTerminal());
    }
    private boolean hasUnsatisfiedMandatory(ExecutionNode node, CapabilityContext context) {
        return context.missingRequirements().stream()
                .anyMatch(requirement -> requirement.required()
                        && !requirement.acceptsPartialContext());
    }

    private void validateResult(CapabilityMetadata metadata, CapabilityResult result) {
        for (ProducedArtifact artifact : result.artifacts()) {
            boolean declared = metadata.producedContributions().stream()
                    .filter(ProducedContribution.ArtifactContribution.class::isInstance)
                    .map(ProducedContribution.ArtifactContribution.class::cast)
                    .anyMatch(output -> output.type().equals(artifact.type())
                            && output.version().equals(artifact.version()));
            if (!declared) throw new IllegalArgumentException("Capability produced undeclared artifact");
        }
    }
    private CapabilityFailure failure(
            String type, String code, boolean retryable, UUID producer, Throwable exception) {
        return new CapabilityFailure(
                type, code, exception.getMessage() == null ? type : exception.getMessage(),
                retryable, null, producer, clock.instant(), Map.of());
    }
    private CapabilityExecution persist(
            CapabilityExecution execution, List<CapabilityExecution> history) {
        executions.save(execution);
        history.add(execution);
        return execution;
    }
    private void save(
            CapabilityExecution execution, Map<UUID, CapabilityExecution> current,
            List<CapabilityExecution> history, UUID nodeId) {
        persist(execution, history);
        current.put(nodeId, execution);
    }
    private void cancelRemaining(
            Map<UUID, CapabilityExecution> current, List<CapabilityExecution> history) {
        current.replaceAll((node, execution) -> {
            if (execution.state().isTerminal()) return execution;
            return persist(execution.transitionTo(
                    CapabilityExecutionState.CANCELLED, clock.instant()), history);
        });
    }
    private ExecutionSummary summary(
            ExecutionPlan plan, ExecutionControl control,
            List<CapabilityExecution> history, int accepted, int late) {
        ExecutionEngineState state = control.isCancellationRequested()
                ? ExecutionEngineState.CANCELLED : ExecutionEngineState.COMPLETED;
        return new ExecutionSummary(
                plan.id(), plan.analysisExecutionId(), state,
                executions.findByAnalysisExecutionId(plan.analysisExecutionId()),
                accepted, late);
    }
    private record RunningInvocation(
            ExecutionNode node, CapabilityExecution execution,
            CapabilityExecutionHandle handle) {}
}
