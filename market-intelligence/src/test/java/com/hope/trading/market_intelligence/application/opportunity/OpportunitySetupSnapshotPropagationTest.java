package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryObservationRepository;
import com.hope.trading.market_intelligence.adapter.persistence.InMemoryTradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0029: the setup snapshot flows through opportunity creation and
 * survives status transitions verbatim — the setup is historical truth,
 * while status and validity evolve.
 */
class OpportunitySetupSnapshotPropagationTest {

    @Test
    void createdOpportunityCarriesSnapshotAndTransitionsPreserveIt() {
        var observation = OpportunityTestFixtures.observation();
        var observationStore = new InMemoryObservationRepository();
        observationStore.save(observation);
        OpportunityEngine engine = new OpportunityEngine(
                observationStore, references -> true,
                new InMemoryTradingOpportunityRepository(),
                new DeterministicOpportunityFusionPolicy(),
                new OpportunityDeduplicationPolicy(Duration.ofMinutes(15)),
                new OpportunityLifecyclePolicy(), new OpportunityFactory(),
                () -> new OpportunityId(UUID.randomUUID()),
                Clock.fixed(OpportunityTestFixtures.NOW, ZoneOffset.UTC));

        OpportunitySetupSnapshot snapshot = new OpportunitySetupSnapshot(
                new BigDecimal("64120.50"), OpportunityTestFixtures.NOW,
                "Price broke resistance with momentum",
                List.of(new OpportunityTrigger("directional_price_change", "12.5")),
                OpportunityTestFixtures.NOW);
        CreateOpportunityCommand command = new CreateOpportunityCommand(
                "BTC/EUR", OpportunityDirection.LONG, "Bullish breakout", "5m",
                OpportunityOrigin.PASSIVE_SCAN,
                java.util.Set.of(new ObservationReference(observation.id())),
                java.util.Set.of(),
                OpportunityTestFixtures.NOW, OpportunityTestFixtures.NOW.plusSeconds(300),
                OpportunityTestFixtures.MATCH_ID, null, snapshot);

        OpportunityId id = engine.create(command).opportunity().id();
        TradingOpportunity detected = engine.transition(id, OpportunityStatus.ANALYZED);
        TradingOpportunity active = engine.transition(id, OpportunityStatus.ACTIVE);

        assertThat(detected.setup()).contains(snapshot);
        // Historical setup truth is immutable across version transitions.
        assertThat(active.setup()).contains(snapshot);
        assertThat(active.status()).isEqualTo(OpportunityStatus.ACTIVE);
    }
}
