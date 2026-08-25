package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class OpportunityEngineTest {
    @Test
    void createsThenVersionsEquivalentOpportunityWithoutOverwritingHistory() {
        Observation observation = OpportunityTestFixtures.observation();
        InMemoryObservationRepository observationStore = new InMemoryObservationRepository();
        observationStore.save(observation);
        InMemoryTradingOpportunityRepository opportunities =
                new InMemoryTradingOpportunityRepository();
        OpportunityEngine engine = engine(observationStore, opportunities);

        OpportunityCreationResult first = engine.create(
                OpportunityTestFixtures.command(observation));
        OpportunityCreationResult second = engine.create(
                OpportunityTestFixtures.command(observation));

        assertThat(first).isInstanceOf(OpportunityCreationResult.Created.class);
        assertThat(second).isInstanceOf(OpportunityCreationResult.VersionCreated.class);
        assertThat(second.opportunity().id()).isEqualTo(first.opportunity().id());
        assertThat(second.opportunity().version().value()).isEqualTo(2);
        assertThat(opportunities.findHistory(first.opportunity().id()))
                .extracting(item -> item.version().value()).containsExactly(1L, 2L);
    }

    @Test
    void rejectsUnknownObservationAndSupportsAbsentAi() {
        InMemoryObservationRepository observations = new InMemoryObservationRepository();
        OpportunityEngine engine = engine(
                observations, new InMemoryTradingOpportunityRepository());
        CreateOpportunityCommand command = OpportunityTestFixtures.command(
                OpportunityTestFixtures.observation());

        assertThat(command.aiAnalyses()).isEmpty();
        assertThatThrownBy(() -> engine.create(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Observation");
    }

    @Test
    void preservesOptionalAiReferencesWhenCatalogValidatesThem() {
        Observation observation = OpportunityTestFixtures.observation();
        InMemoryObservationRepository observationStore = new InMemoryObservationRepository();
        observationStore.save(observation);
        AiAnalysisReference ai = new AiAnalysisReference(UUID.randomUUID());
        CreateOpportunityCommand base = OpportunityTestFixtures.command(observation);
        CreateOpportunityCommand command = new CreateOpportunityCommand(
                base.instrument(), base.direction(), base.scenario(), base.timeframe(),
                base.origin(), base.observations(), Set.of(ai), base.evaluatedAt(),
                base.validUntil(), base.strategyMatchId(), base.opportunityId(),
                base.setupSnapshot());
        OpportunityEngine engine = new OpportunityEngine(
                observationStore, references -> references.equals(Set.of(ai)),
                new InMemoryTradingOpportunityRepository(),
                new DeterministicOpportunityFusionPolicy(),
                new OpportunityDeduplicationPolicy(Duration.ofMinutes(15)),
                new OpportunityLifecyclePolicy(), new OpportunityFactory(),
                () -> new OpportunityId(UUID.randomUUID()),
                Clock.fixed(OpportunityTestFixtures.NOW, ZoneOffset.UTC));

        assertThat(engine.create(command).opportunity().aiAnalyses()).containsExactly(ai);
    }

    @Test
    void transitionsAlwaysAppendAVersionAndRejectTerminalTransitions() {
        Observation observation = OpportunityTestFixtures.observation();
        InMemoryObservationRepository observationStore = new InMemoryObservationRepository();
        observationStore.save(observation);
        InMemoryTradingOpportunityRepository repository =
                new InMemoryTradingOpportunityRepository();
        OpportunityEngine engine = engine(observationStore, repository);
        OpportunityId id = engine.create(
                OpportunityTestFixtures.command(observation)).opportunity().id();

        engine.transition(id, OpportunityStatus.ANALYZED);
        engine.transition(id, OpportunityStatus.ACTIVE);
        engine.transition(id, OpportunityStatus.CONSUMED);

        assertThat(repository.findHistory(id)).hasSize(4);
        assertThatThrownBy(() -> engine.transition(id, OpportunityStatus.ACTIVE))
                .isInstanceOf(IllegalOpportunityTransitionException.class);
    }

    private OpportunityEngine engine(
            ObservationRepository observations,
            TradingOpportunityRepository opportunities) {
        return new OpportunityEngine(
                observations, references -> references.isEmpty(), opportunities,
                new DeterministicOpportunityFusionPolicy(),
                new OpportunityDeduplicationPolicy(Duration.ofMinutes(15)),
                new OpportunityLifecyclePolicy(), new OpportunityFactory(),
                () -> new OpportunityId(UUID.fromString(
                        "11111111-1111-1111-1111-111111111111")),
                Clock.fixed(OpportunityTestFixtures.NOW, ZoneOffset.UTC));
    }
}
