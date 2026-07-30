package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultOpportunityRegistryTest {
    @Test
    void resolvesCurrentHistoryActiveAndDelegatesLifecycleAndExpiration() {
        Observation observation = OpportunityTestFixtures.observation();
        InMemoryObservationRepository observationStore = new InMemoryObservationRepository();
        observationStore.save(observation);
        InMemoryTradingOpportunityRepository repository =
                new InMemoryTradingOpportunityRepository();
        OpportunityEngine engine = new OpportunityEngine(
                observationStore, references -> references.isEmpty(), repository,
                new DeterministicOpportunityFusionPolicy(),
                new OpportunityDeduplicationPolicy(Duration.ofMinutes(15)),
                new OpportunityLifecyclePolicy(), new OpportunityFactory(),
                () -> new OpportunityId(UUID.randomUUID()),
                Clock.fixed(OpportunityTestFixtures.NOW, ZoneOffset.UTC));
        DefaultOpportunityRegistry registry =
                new DefaultOpportunityRegistry(repository, engine);
        OpportunityId id = engine.create(
                OpportunityTestFixtures.command(observation)).opportunity().id();

        registry.transition(id, OpportunityStatus.ANALYZED);
        registry.transition(id, OpportunityStatus.ACTIVE);

        assertThat(registry.active()).hasSize(1);
        assertThat(registry.latest(id).orElseThrow().version().value()).isEqualTo(3);
        assertThat(registry.history(id)).hasSize(3);

        assertThat(registry.expireDue(
                new ValidityWindowExpirationPolicy(),
                OpportunityTestFixtures.NOW.plusSeconds(300))).hasSize(1);
        assertThat(registry.active()).isEmpty();
        assertThat(registry.latest(id).orElseThrow().status())
                .isEqualTo(OpportunityStatus.EXPIRED);
        assertThat(registry.history(id)).hasSize(4);
    }
}
