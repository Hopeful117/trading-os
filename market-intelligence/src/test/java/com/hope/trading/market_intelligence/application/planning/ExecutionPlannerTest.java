package com.hope.trading.market_intelligence.application.planning;

import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.planning.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ExecutionPlannerTest {
    private final Clock clock = Clock.fixed(CapabilityTestFixtures.NOW, ZoneOffset.UTC);

    @Test
    void buildsLinearPlanAndResolvesProducer() {
        Capability producer = cap("producer", List.of(),
                List.of(CapabilityTestFixtures.produces(
                        CapabilityTestFixtures.INTERMEDIATE, CapabilityTestFixtures.V1)));
        Capability consumer = cap("consumer", List.of(CapabilityTestFixtures.requirement(
                CapabilityTestFixtures.INTERMEDIATE, CapabilityTestFixtures.V1)), List.of());

        ExecutionPlan plan = planner(producer, consumer).plan(request());

        assertThat(plan.nodes()).hasSize(2);
        assertThat(plan.edges()).hasSize(1);
        assertThat(plan.selectedProducers()).hasSize(1);
    }

    @Test
    void exposesIndependentParallelBranches() {
        Capability first = cap("first", List.of(), List.of());
        Capability second = cap("second", List.of(), List.of());

        ExecutionPlan plan = planner(first, second).plan(request());

        assertThat(plan.edges()).isEmpty();
        assertThat(plan.nodes().values())
                .allMatch(node -> node.incomingDependencies().isEmpty());
    }

    @Test
    void rejectsAmbiguousAndImpossibleRequirements() {
        Capability p1 = cap("p1", List.of(), List.of(CapabilityTestFixtures.produces(
                CapabilityTestFixtures.OUTPUT, CapabilityTestFixtures.V1)));
        Capability p2 = cap("p2", List.of(), List.of(CapabilityTestFixtures.produces(
                CapabilityTestFixtures.OUTPUT, CapabilityTestFixtures.V1)));
        Capability consumer = cap("consumer", List.of(CapabilityTestFixtures.requirement(
                CapabilityTestFixtures.OUTPUT, CapabilityTestFixtures.V1)), List.of());

        assertFailure(planner(p1, p2, consumer), PlanningFailureType.AMBIGUOUS_ARTIFACT_PRODUCER);
        assertFailure(planner(consumer), PlanningFailureType.NO_COMPATIBLE_ARTIFACT_PRODUCER);
    }

    @Test
    void rejectsCycle() {
        Capability a = cap("a", List.of(CapabilityTestFixtures.requirement(
                CapabilityTestFixtures.OUTPUT, CapabilityTestFixtures.V1)),
                List.of(CapabilityTestFixtures.produces(
                        CapabilityTestFixtures.INTERMEDIATE, CapabilityTestFixtures.V1)));
        Capability b = cap("b", List.of(CapabilityTestFixtures.requirement(
                CapabilityTestFixtures.INTERMEDIATE, CapabilityTestFixtures.V1)),
                List.of(CapabilityTestFixtures.produces(
                        CapabilityTestFixtures.OUTPUT, CapabilityTestFixtures.V1)));

        assertFailure(planner(a, b), PlanningFailureType.CYCLIC_DEPENDENCY);
    }

    @Test
    void validatesExactAndExplicitBackwardCompatibility() {
        ArtifactRequirement backward = new ArtifactRequirement(
                CapabilityTestFixtures.OUTPUT, CapabilityTestFixtures.V1,
                VersionCompatibilityMode.BACKWARD_COMPATIBLE, true,
                ArtifactCardinality.ONE, false);
        ProducedContribution.ArtifactContribution compatible =
                new ProducedContribution.ArtifactContribution(
                        CapabilityTestFixtures.OUTPUT, CapabilityTestFixtures.V2,
                        Set.of(CapabilityTestFixtures.V1));
        Capability producer = cap("producer", List.of(), List.of(compatible));
        Capability consumer = cap("consumer", List.of(backward), List.of());

        assertThat(planner(producer, consumer).plan(request()).edges()).hasSize(1);

        Capability incompatible = cap("incompatible", List.of(), List.of(
                CapabilityTestFixtures.produces(
                        CapabilityTestFixtures.OUTPUT, CapabilityTestFixtures.V2)));
        assertFailure(
                planner(incompatible, consumer),
                PlanningFailureType.NO_COMPATIBLE_ARTIFACT_PRODUCER);
    }

    @Test
    void insertsDirectArtifactAdapterAndRejectsImplicitChain() {
        Capability producer = cap("producer", List.of(), List.of(
                CapabilityTestFixtures.produces(
                        CapabilityTestFixtures.OUTPUT, CapabilityTestFixtures.V2)));
        Capability consumer = cap("consumer", List.of(CapabilityTestFixtures.requirement(
                CapabilityTestFixtures.OUTPUT, CapabilityTestFixtures.V1)), List.of());
        CapabilityRegistry registry = registry(producer, consumer);
        ArtifactAdapterRegistry adapters = new ArtifactAdapterRegistry();
        adapters.register(adapter(CapabilityTestFixtures.V2, CapabilityTestFixtures.V1));

        ExecutionPlan plan = new ExecutionPlanner(registry, adapters, clock).plan(request());

        assertThat(plan.adapters()).hasSize(1);
        assertThat(plan.constraints()).contains("DIRECT_ADAPTERS_ONLY");
    }

    private ExecutionPlanner planner(Capability... capabilities) {
        return new ExecutionPlanner(
                registry(capabilities), new ArtifactAdapterRegistry(), clock);
    }
    private CapabilityRegistry registry(Capability... capabilities) {
        CapabilityRegistry registry = new CapabilityRegistry();
        Arrays.stream(capabilities).forEach(registry::register);
        return registry;
    }
    private Capability cap(
            String id, List<ArtifactRequirement> requirements,
            List<ProducedContribution> produced) {
        return CapabilityTestFixtures.capability(
                id, requirements, produced, RetryPolicy.disabled(),
                context -> CapabilityResult.noOpportunity(List.of()));
    }
    private PlanningRequest request() {
        return new PlanningRequest(
                UUID.randomUUID(), Set.of(), Set.of(), Set.of());
    }
    private void assertFailure(ExecutionPlanner planner, PlanningFailureType expected) {
        assertThatThrownBy(() -> planner.plan(request()))
                .isInstanceOf(ExecutionPlanningException.class)
                .extracting(exception -> ((ExecutionPlanningException) exception)
                        .failure().type())
                .isEqualTo(expected);
    }
    private ArtifactAdapter adapter(ArtifactVersion source, ArtifactVersion target) {
        return new ArtifactAdapter() {
            @Override public ArtifactType artifactType() {
                return CapabilityTestFixtures.OUTPUT;
            }
            @Override public ArtifactVersion sourceVersion() { return source; }
            @Override public ArtifactVersion targetVersion() { return target; }
            @Override public com.hope.trading.market_intelligence.domain.artifact.StoredArtifact adapt(
                    com.hope.trading.market_intelligence.domain.artifact.StoredArtifact artifact) {
                return artifact;
            }
        };
    }
}
