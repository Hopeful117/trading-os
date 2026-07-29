package com.hope.trading.market_intelligence.application.planning;

import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.planning.*;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

/** Builds and validates a DAG. It never invokes a Capability. */
public class ExecutionPlanner {
    private final CapabilityRegistry capabilities;
    private final ArtifactAdapterRegistry adapters;
    private final Clock clock;

    public ExecutionPlanner(
            CapabilityRegistry capabilities, ArtifactAdapterRegistry adapters, Clock clock) {
        this.capabilities = capabilities;
        this.adapters = adapters;
        this.clock = clock;
    }

    public ExecutionPlan plan(PlanningRequest request) {
        List<PlanningDecision> decisions = new ArrayList<>();
        List<PlanningDecision> exclusions = new ArrayList<>();
        List<Capability> selected = capabilities.all().stream()
                .filter(capability -> selected(capability, request, decisions, exclusions))
                .toList();

        Map<Capability, UUID> ids = selected.stream().collect(Collectors.toMap(
                capability -> capability, ignored -> UUID.randomUUID()));
        Set<ExecutionPlan.PlanEdge> edges = new HashSet<>();
        Map<RequirementBinding, Set<UUID>> producers = new HashMap<>();
        List<AdapterBinding> adapterBindings = new ArrayList<>();

        for (Capability consumer : selected) {
            UUID consumerId = ids.get(consumer);
            for (ArtifactRequirement requirement : consumer.metadata().requirements()) {
                Optional<ArtifactAdapter> initialAdapter =
                        initialAdapter(requirement, request.initialArtifacts());
                if (providedInitially(requirement, request.initialArtifacts())) {
                    initialAdapter.ifPresent(adapter -> adapterBindings.add(
                            new AdapterBinding(null, consumerId, requirement, adapter)));
                    continue;
                }
                List<Candidate> candidates = candidates(requirement, selected, ids);
                if (candidates.isEmpty()) {
                    if (!requirement.required() || requirement.acceptsPartialContext()) continue;
                    fail(PlanningFailureType.NO_COMPATIBLE_ARTIFACT_PRODUCER,
                            consumer, requirement, "No compatible producer or direct adapter");
                }
                if (requirement.cardinality() == ArtifactCardinality.ONE
                        && candidates.size() > 1)
                    fail(PlanningFailureType.AMBIGUOUS_ARTIFACT_PRODUCER,
                            consumer, requirement, "Several compatible producers");
                List<Candidate> retained = requirement.cardinality() == ArtifactCardinality.ONE
                        ? List.of(candidates.getFirst()) : candidates;
                Set<UUID> producerIds = new HashSet<>();
                for (Candidate candidate : retained) {
                    producerIds.add(candidate.nodeId());
                    edges.add(new ExecutionPlan.PlanEdge(
                            candidate.nodeId(), consumerId, requirement));
                    if (candidate.adapter() != null)
                        adapterBindings.add(new AdapterBinding(
                                candidate.nodeId(), consumerId, requirement, candidate.adapter()));
                }
                producers.put(new RequirementBinding(consumerId, requirement), producerIds);
            }
        }

        Map<UUID, Set<UUID>> incoming = new HashMap<>();
        Map<UUID, Set<UUID>> outgoing = new HashMap<>();
        ids.values().forEach(id -> { incoming.put(id, new HashSet<>()); outgoing.put(id, new HashSet<>()); });
        edges.forEach(edge -> {
            incoming.get(edge.to()).add(edge.from());
            outgoing.get(edge.from()).add(edge.to());
        });
        List<ExecutionNode> nodes = selected.stream().map(capability -> {
            UUID id = ids.get(capability);
            List<ProducedContribution.ArtifactContribution> expected =
                    capability.metadata().producedContributions().stream()
                            .filter(ProducedContribution.ArtifactContribution.class::isInstance)
                            .map(ProducedContribution.ArtifactContribution.class::cast).toList();
            return new ExecutionNode(
                    id, capability,
                    CapabilityExecution.created(
                            request.analysisExecutionId(), capability.metadata(), clock.instant()),
                    capability.metadata().requirements(), incoming.get(id), outgoing.get(id), expected);
        }).toList();
        return new ExecutionPlan(
                UUID.randomUUID(), request.analysisExecutionId(), nodes, edges,
                producers, adapterBindings, List.of("DIRECT_ADAPTERS_ONLY"),
                decisions, exclusions);
    }

    private boolean selected(
            Capability capability, PlanningRequest request,
            List<PlanningDecision> decisions, List<PlanningDecision> exclusions) {
        CapabilityMetadata metadata = capability.metadata();
        boolean selected = switch (metadata.executionPolicy()) {
            case REQUIRED -> true;
            case OPTIONAL, ON_DEMAND -> request.explicitlySelected().contains(metadata.id());
            case CONDITIONAL -> request.satisfiedConditions().contains(metadata.conditionId());
        };
        (selected ? decisions : exclusions).add(new PlanningDecision(
                metadata.id(), selected, selected ? "POLICY_SELECTED" : "POLICY_NOT_SELECTED"));
        return selected;
    }

    private boolean providedInitially(
            ArtifactRequirement requirement, Set<ArtifactDescriptor> initial) {
        return initial.stream().anyMatch(artifact ->
                artifact.type().equals(requirement.artifactType())
                        && (artifact.version().equals(requirement.expectedVersion())
                        || adapters.find(artifact.type(), artifact.version(),
                        requirement.expectedVersion()).isPresent()));
    }

    private Optional<ArtifactAdapter> initialAdapter(
            ArtifactRequirement requirement, Set<ArtifactDescriptor> initial) {
        return initial.stream()
                .filter(artifact -> artifact.type().equals(requirement.artifactType()))
                .filter(artifact -> !artifact.version().equals(requirement.expectedVersion()))
                .map(artifact -> adapters.find(
                        artifact.type(), artifact.version(), requirement.expectedVersion()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private List<Candidate> candidates(
            ArtifactRequirement requirement, List<Capability> selected,
            Map<Capability, UUID> ids) {
        List<Candidate> result = new ArrayList<>();
        for (Capability capability : selected) {
            for (ProducedContribution contribution : capability.metadata().producedContributions()) {
                if (!(contribution instanceof ProducedContribution.ArtifactContribution artifact)
                        || !artifact.type().equals(requirement.artifactType())) continue;
                if (artifact.satisfies(requirement))
                    result.add(new Candidate(ids.get(capability), null));
                else adapters.find(artifact.type(), artifact.version(),
                                requirement.expectedVersion())
                        .ifPresent(adapter -> result.add(
                                new Candidate(ids.get(capability), adapter)));
            }
        }
        return result;
    }

    private void fail(
            PlanningFailureType type, Capability capability,
            ArtifactRequirement requirement, String message) {
        throw new ExecutionPlanningException(new PlanningFailure(
                type, message, capability.metadata().id(), requirement));
    }
    private record Candidate(UUID nodeId, ArtifactAdapter adapter) {}
}
