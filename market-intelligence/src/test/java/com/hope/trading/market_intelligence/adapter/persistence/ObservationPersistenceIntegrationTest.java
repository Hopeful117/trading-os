package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.observation.ObservationQueryService;
import com.hope.trading.market_intelligence.domain.observation.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationPersistenceIntegrationTest {
    @Test
    void persistsAndQueriesMetadataEvidenceRelationshipsAndTraceability() {
        InMemoryObservationRepository repository = new InMemoryObservationRepository();
        ObservationFactory factory = new ObservationFactory();
        ObservationEvidence evidence = ObservationTestFixtures.evidence(new BigDecimal("0.75"));
        Observation observation = factory.create(
                UUID.randomUUID(), 1, "BTC/EUR", new ObservationType("LIQUIDITY"),
                "Liquid", "Deterministic conclusion", Set.of("market"), "5m",
                ObservationTestFixtures.NOW, ObservationTestFixtures.NOW,
                ObservationTestFixtures.NOW.plusSeconds(60), null, "rule-v1",
                List.of(evidence));
        repository.save(observation);
        ObservationQueryService queries = new ObservationQueryService(repository);

        assertThat(queries.findByInstrument("btc/eur")).containsExactly(observation);
        assertThat(queries.findActive()).containsExactly(observation);
        assertThat(queries.findByType(new ObservationType("LIQUIDITY")))
                .containsExactly(observation);
        assertThat(queries.findByStatus(ObservationStatus.ACTIVE))
                .containsExactly(observation);
        assertThat(queries.findByHorizon("5M")).containsExactly(observation);
        assertThat(queries.findByCategory("MARKET")).containsExactly(observation);
        assertThat(queries.findByConfidence(
                new BigDecimal("0.7"), new BigDecimal("0.8"))).containsExactly(observation);
        assertThat(queries.findByTimeRange(
                ObservationTestFixtures.NOW.minusSeconds(1),
                ObservationTestFixtures.NOW.plusSeconds(1))).containsExactly(observation);

        Observation reloaded = repository.findById(observation.id()).orElseThrow();
        assertThat(reloaded.evidence()).singleElement().satisfies(item ->
                assertThat(item.capabilityResult().artifacts()).singleElement()
                        .satisfies(artifact ->
                                assertThat(artifact.rawMarketData()).isNotEmpty()));
    }
}
