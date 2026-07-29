package com.hope.trading.market_intelligence.domain.planning;

import com.hope.trading.market_intelligence.domain.capability.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExecutionPlan {
    private final UUID id;
    private final UUID analysisExecutionId;
    private final Map<UUID, ExecutionNode> nodes;
    private final Set<PlanEdge> edges;
    private final Map<RequirementBinding, Set<UUID>> selectedProducers;
    private final List<AdapterBinding> adapters;
    private final List<String> constraints;
    private final List<PlanningDecision> decisions;
    private final List<PlanningDecision> exclusions;

    public ExecutionPlan(
            UUID id, UUID analysisExecutionId, Collection<ExecutionNode> nodes,
            Set<PlanEdge> edges, Map<RequirementBinding, Set<UUID>> selectedProducers,
            List<AdapterBinding> adapters, List<String> constraints,
            List<PlanningDecision> decisions, List<PlanningDecision> exclusions) {
        this.id = Objects.requireNonNull(id);
        this.analysisExecutionId = Objects.requireNonNull(analysisExecutionId);
        this.nodes = nodes.stream().collect(Collectors.toUnmodifiableMap(
                ExecutionNode::id, Function.identity()));
        this.edges = Set.copyOf(edges);
        this.selectedProducers = selectedProducers.entrySet().stream().collect(
                Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        this.adapters = List.copyOf(adapters);
        this.constraints = List.copyOf(constraints);
        this.decisions = List.copyOf(decisions);
        this.exclusions = List.copyOf(exclusions);
        validate();
    }

    private void validate() {
        edges.forEach(edge -> {
            if (!nodes.containsKey(edge.from()) || !nodes.containsKey(edge.to()))
                throw new IllegalArgumentException("Plan edge references unknown node");
        });
        Map<UUID, Integer> degrees = nodes.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> 0));
        edges.forEach(edge -> degrees.compute(edge.to(), (key, value) -> value + 1));
        Deque<UUID> ready = new ArrayDeque<>();
        degrees.forEach((node, degree) -> { if (degree == 0) ready.add(node); });
        int visited = 0;
        while (!ready.isEmpty()) {
            UUID node = ready.remove();
            visited++;
            for (PlanEdge edge : edges) if (edge.from().equals(node)
                    && degrees.compute(edge.to(), (key, value) -> value - 1) == 0)
                ready.add(edge.to());
        }
        if (visited != nodes.size()) throw new ExecutionPlanningException(
                new PlanningFailure(PlanningFailureType.CYCLIC_DEPENDENCY,
                        "Execution plan contains a cycle", null, null));
    }

    public UUID id() { return id; }
    public UUID analysisExecutionId() { return analysisExecutionId; }
    public Map<UUID, ExecutionNode> nodes() { return nodes; }
    public Set<PlanEdge> edges() { return edges; }
    public Map<RequirementBinding, Set<UUID>> selectedProducers() { return selectedProducers; }
    public List<AdapterBinding> adapters() { return adapters; }
    public List<String> constraints() { return constraints; }
    public List<PlanningDecision> decisions() { return decisions; }
    public List<PlanningDecision> exclusions() { return exclusions; }
    public record PlanEdge(UUID from, UUID to, ArtifactRequirement requirement) {}
}
