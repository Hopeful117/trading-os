package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;
import com.hope.trading.market_intelligence.domain.observation.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ObservationBuilderTest {
    private final UUID analysisId = UUID.randomUUID();
    private final InMemoryCapabilityExecutionRepository executions =
            new InMemoryCapabilityExecutionRepository();
    private final InMemoryObservationRepository observations =
            new InMemoryObservationRepository();
    private final ObservationBuilder builder = new ObservationBuilder(
            executions, observations, new ObservationFactory(),
            Clock.fixed(ObservationTestFixtures.NOW, ZoneOffset.UTC));

    @BeforeEach
    void resultExists() {
        executions.save(ObservationTestFixtures.completed(analysisId, "spread-analysis"));
    }

    @Test
    void consolidatesCapabilityResultsIntoTraceableObservation() {
        Observation observation = builder.build(analysisId, "BTC/EUR", rule("v1", "0.8"));

        assertThat(observation.version()).isEqualTo(1);
        assertThat(observation.status()).isEqualTo(ObservationStatus.ACTIVE);
        assertThat(observation.confidence().score()).isEqualByComparingTo("0.8000");
        assertThat(observation.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.capabilityResult().capabilityId())
                    .isEqualTo("spread-analysis");
            assertThat(evidence.capabilityResult().artifacts()).singleElement()
                    .satisfies(artifact -> assertThat(artifact.rawMarketData()).hasSize(1));
        });
    }

    @Test
    void newBuildCreatesVersionAndBidirectionalSupersession() {
        Observation first = builder.build(analysisId, "BTC/EUR", rule("v1", "0.7"));
        Observation second = builder.build(analysisId, "BTC/EUR", rule("v2", "0.9"));

        Observation persistedFirst = observations.findById(first.id()).orElseThrow();
        assertThat(second.version()).isEqualTo(2);
        assertThat(second.lineageId()).isEqualTo(first.lineageId());
        assertThat(second.supersedes()).contains(first.id());
        assertThat(persistedFirst.status()).isEqualTo(ObservationStatus.SUPERSEDED);
        assertThat(persistedFirst.supersededBy()).contains(second.id());
    }

    @Test
    void lifecycleExpirationReturnsNewImmutableRepresentation() {
        Observation active = builder.build(analysisId, "BTC/EUR", rule("v1", "0.7"));
        Observation expired = builder.expire(active.id());

        assertThat(active.status()).isEqualTo(ObservationStatus.ACTIVE);
        assertThat(expired.status()).isEqualTo(ObservationStatus.EXPIRED);
        assertThat(expired).isNotSameAs(active);
    }

    private ObservationConsolidationRule rule(String version, String contribution) {
        return new ObservationConsolidationRule() {
            @Override public String version() { return version; }
            @Override public ObservationRuleResult evaluate(
                    String instrument, List<CapabilityExecution> results) {
                CapabilityExecution execution = results.getFirst();
                return new ObservationRuleResult(
                        new ObservationType("LIQUID_MARKET"), "Liquid market",
                        "Spread satisfies the deterministic limit", Set.of("liquidity"),
                        "5m", ObservationTestFixtures.NOW,
                        ObservationTestFixtures.NOW.plusSeconds(300),
                        List.of(new ObservationEvidenceCandidate(
                                execution.id(), "Tight spread", "Spread is below maximum",
                                Map.of("spread", new BigDecimal("0.002")),
                                Map.of("maximum", new BigDecimal("0.005")),
                                ObservationTestFixtures.NOW, new BigDecimal(contribution))));
            }
        };
    }
}
