package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryObservationRepository;
import com.hope.trading.market_intelligence.adapter.persistence.InMemoryTradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.opportunity.ObservationReference;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityFactory;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0018 proofs: the expiration driver makes the existing lifecycle
 * effective — due opportunities expire, not-due ones don't, ticks are
 * idempotent, missed expirations are caught up after downtime.
 */
class OpportunityExpirationDriverTest {

    private static final Instant NOW = OpportunityTestFixtures.NOW;
    /** Fixture commands carry validUntil = NOW + 300s. */
    private static final Instant VALID_UNTIL = NOW.plusSeconds(300);

    @Test
    void dueActiveOpportunityExpiresAndDisappearsFromActive() {
        TestContext context = new TestContext(NOW);
        OpportunityId id = context.createActivatedOpportunity("BTC/EUR");

        // Not yet due: remains active.
        context.driver.expireDueOpportunities();
        assertThat(context.registry.latest(id).orElseThrow().status())
                .isEqualTo(OpportunityStatus.ACTIVE);
        assertThat(context.registry.active()).hasSize(1);

        // Due: expires, leaves findActive, appends exactly one version.
        context.clock.set(NOW.plus(Duration.ofMinutes(6)));
        context.driver.expireDueOpportunities();
        assertThat(context.registry.latest(id).orElseThrow().status())
                .isEqualTo(OpportunityStatus.EXPIRED);
        assertThat(context.registry.active()).isEmpty();

        long versionsAfterFirstExpiration =
                context.registry.latest(id).orElseThrow().version().value();
        assertThat(versionsAfterFirstExpiration).isEqualTo(4L);

        // Idempotence: second tick adds no further transition/version.
        context.clock.set(NOW.plus(Duration.ofMinutes(7)));
        context.driver.expireDueOpportunities();
        assertThat(context.registry.latest(id).orElseThrow().status())
                .isEqualTo(OpportunityStatus.EXPIRED);
        assertThat(context.registry.latest(id).orElseThrow().version().value())
                .isEqualTo(versionsAfterFirstExpiration);
        assertThat(context.registry.history(id)).hasSize(4);
    }

    @Test
    void expirationMissedWhileDownIsCaughtUpByNextTick() {
        TestContext context = new TestContext(NOW);
        OpportunityId id = context.createActivatedOpportunity("BTC/EUR");
        long versionsBefore = context.registry.latest(id).orElseThrow().version().value();

        // No tick happens around validUntil (application down); the first
        // available tick is far later and must still expire the opportunity.
        context.clock.set(NOW.plus(Duration.ofHours(2)));
        context.driver.expireDueOpportunities();

        assertThat(context.registry.latest(id).orElseThrow().status())
                .isEqualTo(OpportunityStatus.EXPIRED);
        assertThat(context.registry.latest(id).orElseThrow().version().value())
                .isEqualTo(versionsBefore + 1L);
    }

    @Test
    void onlyDueOpportunitiesExpireAmongMany() {
        TestContext context = new TestContext(NOW);
        OpportunityId due = context.createActivatedOpportunity("BTC/EUR");
        OpportunityId alsoDue = context.createActivatedOpportunity("ETH/EUR");
        OpportunityId noWindow =
                context.createActivatedOpportunityWithoutValidityWindow("SOL/EUR");

        context.clock.set(NOW.plus(Duration.ofMinutes(6)));
        context.driver.expireDueOpportunities();

        assertThat(context.registry.latest(due).orElseThrow().status())
                .isEqualTo(OpportunityStatus.EXPIRED);
        assertThat(context.registry.latest(alsoDue).orElseThrow().status())
                .isEqualTo(OpportunityStatus.EXPIRED);
        // Absent validUntil: existing policy keeps the opportunity; the
        // driver adds no rule of its own.
        assertThat(context.registry.latest(noWindow).orElseThrow().status())
                .isEqualTo(OpportunityStatus.ACTIVE);
        assertThat(context.registry.active()).hasSize(1);
    }

    // ---- helpers -----------------------------------------------------------

    /** Mutable fixed clock so tests control time explicitly. */
    static final class SettableClock extends Clock {
        private volatile Instant instant;

        SettableClock(Instant initial) {
            this.instant = initial;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return instant;
        }
    }

    private static final class TestContext {
        final SettableClock clock;
        final InMemoryTradingOpportunityRepository repository =
                new InMemoryTradingOpportunityRepository();
        final InMemoryObservationRepository observationStore;
        final java.util.Map<String, ObservationReference> observationReferences =
                new java.util.HashMap<>();
        final OpportunityEngine engine;
        final DefaultOpportunityRegistry registry;
        final OpportunityExpirationDriver driver;

        private TestContext(Instant start) {
            this.clock = new SettableClock(start);
            InMemoryObservationRepository observationStore =
                    new InMemoryObservationRepository();
            this.observationStore = observationStore;
            this.engine = new OpportunityEngine(
                    observationStore, references -> references.isEmpty(), repository,
                    new DeterministicOpportunityFusionPolicy(),
                    new OpportunityDeduplicationPolicy(Duration.ofMinutes(15)),
                    new OpportunityLifecyclePolicy(),
                    new OpportunityFactory(),
                    () -> new OpportunityId(UUID.randomUUID()),
                    clock);
            this.registry = new DefaultOpportunityRegistry(repository, engine);
            this.driver = new OpportunityExpirationDriver(
                    registry, new ValidityWindowExpirationPolicy(), clock);
        }

        /** Minimal valid observation for the given instrument. */
        private Observation observation(String instrument) {
            com.hope.trading.market_intelligence.domain.observation.ObservationEvidence
                    evidence = new com.hope.trading.market_intelligence.domain.observation
                            .ObservationEvidence(
                    UUID.randomUUID(), "ohlc-range", "Historical price range",
                    "OHLC range context", java.util.Map.of("priceChange", java.math.BigDecimal.ONE),
                    java.util.Map.of(), NOW, java.math.BigDecimal.ONE, capabilityTrace());
            return new com.hope.trading.market_intelligence.domain.observation.ObservationFactory()
                    .create(UUID.randomUUID(), 1, instrument,
                            new com.hope.trading.market_intelligence.domain.observation
                                    .ObservationType("PRICE_TREND_LONG"),
                            "Directional OHLC trend", "First-to-last change long.",
                            Set.of("price-action"), "15m",
                            NOW.minus(Duration.ofMinutes(5)), NOW.minus(Duration.ofMinutes(5)),
                            VALID_UNTIL, null, "ohlc-trend/v1", java.util.List.of(evidence));
        }

        private com.hope.trading.market_intelligence.domain.observation.CapabilityResultTrace
                capabilityTrace() {
            var raw = new com.hope.trading.market_intelligence.domain.observation
                    .RawMarketDataReference("kraken", "X/Y", "15m", "abc", NOW);
            var artifact = new com.hope.trading.market_intelligence.domain.observation
                    .ArtifactTrace(
                    new com.hope.trading.market_intelligence.domain.artifact.ArtifactIdentity(
                            "OHLC_RANGE_ANALYSIS", "ohlc-range-analysis", "1.0.0"),
                    "1.0.0", "fingerprint", java.util.List.of(raw));
            return new com.hope.trading.market_intelligence.domain.observation
                    .CapabilityResultTrace(
                    UUID.randomUUID(), "ohlc-range-analysis", "1.0.0", java.util.List.of(artifact));
        }

        OpportunityId createActivatedOpportunity(String instrument) {
            return createActivated(command(instrument, VALID_UNTIL));
        }

        OpportunityId createActivatedOpportunityWithoutValidityWindow(String instrument) {
            return createActivated(command(instrument, null));
        }

        private synchronized ObservationReference referenceFor(String instrument) {
            return observationReferences.computeIfAbsent(instrument, key -> {
                Observation observation = observation(key);
                observationStore.save(observation);
                return new ObservationReference(observation.id());
            });
        }

        private OpportunityId createActivated(CreateOpportunityCommand command) {
            OpportunityId id = engine.create(command).opportunity().id();
            registry.transition(id, OpportunityStatus.ANALYZED);
            registry.transition(id, OpportunityStatus.ACTIVE);
            return id;
        }

        private CreateOpportunityCommand command(String instrument, Instant validUntil) {
            return new CreateOpportunityCommand(
                    instrument,
                    OpportunityDirection.LONG,
                    "RANGE_EXPANSION",
                    "15m",
                    OpportunityOrigin.PASSIVE_SCAN,
                    Set.of(referenceFor(instrument)),
                    Set.of(),
                    NOW,
                    validUntil,
                    OpportunityTestFixtures.MATCH_ID,
                    null);
        }
    }
}
