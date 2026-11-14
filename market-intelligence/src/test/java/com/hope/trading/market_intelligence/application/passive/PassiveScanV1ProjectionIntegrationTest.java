package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.adapter.persistence.JpaAnalysisExecutionRepository;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionPolicy;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionProvenance;
import com.hope.trading.market_intelligence.domain.execution.AnalysisTraceMetadata;
import com.hope.trading.market_intelligence.domain.execution.ContextLimits;
import com.hope.trading.market_intelligence.domain.execution.DegradationPolicy;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.execution.RetryPolicy;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.application.port.PassiveScanRunRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactIdentity;
import com.hope.trading.market_intelligence.domain.observation.ArtifactTrace;
import com.hope.trading.market_intelligence.domain.observation.CapabilityResultTrace;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationEvidence;
import com.hope.trading.market_intelligence.domain.observation.ObservationFactory;
import com.hope.trading.market_intelligence.domain.observation.ObservationType;
import com.hope.trading.market_intelligence.domain.observation.RawMarketDataReference;
import com.hope.trading.market_intelligence.domain.opportunity.ObservationReference;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityFactory;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityScore;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityType;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanMarket;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanMarketStatus;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanRun;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisAvailability;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidenceRole;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisScenario;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisState;
import com.hope.trading.market_intelligence.domain.opportunity.MarketThesisReference;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Story 0245 — Integration tests validating the passive scan V1 projection pipeline
 * end-to-end against the real H2 persistence layer.
 *
 * <p>Covers: F-01 (via GatewayDownstreamRoutingIntegrationTest), F-03 (Scenario A),
 * F-04 / R-0242-2 (deduplication), and Scenarios C, D, G.</p>
 *
 * <p>Safety constraints: scheduler disabled (no property set), no broker contact,
 * no TradePlan, no risk evaluation, no AI engine. All temporal assertions use
 * a controlled MutableClock.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PassiveScanV1ProjectionIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private static final String INSTRUMENT = "BTC/EUR";
    private static final String PROVIDER = "KRAKEN";

    @Autowired TradingOpportunityRepository opportunityRepo;
    @Autowired ObservationRepository observationRepo;
    @Autowired MarketThesisRepository thesisRepo;
    @Autowired StrategyMatchRepository strategyMatchRepo;
    @Autowired PassiveScanRunRepository scanRunRepo;
    @Autowired JpaAnalysisExecutionRepository executionRepo;
    @Autowired EntityManager em;
    @MockitoBean MarketDataClient marketDataClient;

    private MutableClock clock;
    private PassiveAwarenessProjectionService projectionService;

    @BeforeEach
    void setup() {
        clock = new MutableClock(NOW);
        projectionService = new PassiveAwarenessProjectionService(
                opportunityRepo, observationRepo, thesisRepo, strategyMatchRepo, marketDataClient, clock);
    }

    // ── Scenario A — F-03: qualifying pipeline produces a current candidate ───

    @Test
    void qualifyingPassiveScanOpportunityAppearsAsCurrentCandidate() {
        UUID marketId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Fixture f = seedFixture(marketId, executionId);

        PassiveAwarenessPage page = projectionService.search(
                new PassiveAwarenessQuery(null, null, null, true, 0, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.current()).isTrue();
            assertThat(item.diagnostics()).isEmpty();
            assertThat(item.opportunity().id()).isEqualTo(f.opportunity().id());
            assertThat(item.opportunity().version().value()).isEqualTo(1L);
            assertThat(item.market().provider()).isEqualTo(PROVIDER);
            assertThat(item.market().symbol()).isEqualTo(INSTRUMENT);
        });
    }

    // ── F-04 / R-0242-2: deduplication ───────────────────────────────────────

    @Test
    void repeatedAppendOfSameOpportunityProducesExactlyOneCurrentCandidate() {
        UUID marketId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Fixture f = seedFixture(marketId, executionId);

        // Append two more versions of the same opportunity (sequential).
        opportunityRepo.append(opportunityVersion(f.opportunity().id(), 2,
                marketId, executionId, f.observation().id(), f.match().matchId(), f.thesis().id(),
                f.thesis().version(), NOW.minusSeconds(5), NOW.plusSeconds(300)));
        opportunityRepo.append(opportunityVersion(f.opportunity().id(), 3,
                marketId, executionId, f.observation().id(), f.match().matchId(), f.thesis().id(),
                f.thesis().version(), NOW.minusSeconds(5), NOW.plusSeconds(300)));

        PassiveAwarenessPage page = projectionService.search(
                new PassiveAwarenessQuery(null, null, null, true, 0, 20));

        assertThat(page.total())
                .as("findLatestPassive returns max version per opportunityId: exactly 1 candidate")
                .isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.current()).isTrue();
            assertThat(item.opportunity().version().value()).isEqualTo(3L);
        });
    }

    // ── Scenario C: stale candidate ──────────────────────────────────────────

    @Test
    void opportunityPastValidityWindowIsNotCurrentWithCurrentOnlyFilter() {
        UUID marketId = UUID.randomUUID();
        seedFixture(marketId, UUID.randomUUID());

        clock.set(NOW.plusSeconds(301)); // past opportunity.validUntil = NOW+300

        PassiveAwarenessPage currentPage = projectionService.search(
                new PassiveAwarenessQuery(null, null, null, true, 0, 20));
        assertThat(currentPage.total())
                .as("stale candidate excluded by currentOnly=true")
                .isEqualTo(0);

        PassiveAwarenessPage allPage = projectionService.search(
                new PassiveAwarenessQuery(null, null, null, false, 0, 20));
        assertThat(allPage.total()).isEqualTo(1);
        assertThat(allPage.items()).singleElement().satisfies(item -> {
            assertThat(item.current()).isFalse();
            assertThat(item.diagnostics()).contains("OPPORTUNITY_OUTSIDE_VALIDITY");
        });
    }

    // ── Scenario D: superseded thesis ────────────────────────────────────────

    @Test
    void supersededThesisMarksExistingCandidateAsNonPositive() {
        UUID marketId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Fixture f = seedFixture(marketId, executionId);

        PassiveAwarenessPage before = projectionService.search(
                new PassiveAwarenessQuery(null, null, null, true, 0, 20));
        assertThat(before.total()).as("candidate is current before supersession").isEqualTo(1);

        // Create a successor thesis that supersedes the original.
        thesisRepo.save(candidateThesis(UUID.randomUUID(), marketId, executionId,
                NOW.minusSeconds(1), NOW.plusSeconds(600), f.thesis().id()));

        PassiveAwarenessPage after = projectionService.search(
                new PassiveAwarenessQuery(null, null, null, true, 0, 20));
        assertThat(after.total())
                .as("superseded thesis excludes candidate from currentOnly feed")
                .isEqualTo(0);

        PassiveAwarenessPage allAfter = projectionService.search(
                new PassiveAwarenessQuery(null, null, null, false, 0, 20));
        assertThat(allAfter.total()).isEqualTo(1);
        assertThat(allAfter.items()).singleElement().satisfies(item -> {
            assertThat(item.current()).isFalse();
            assertThat(item.diagnostics()).contains(PassiveCandidateEvaluator.THESIS_SUPERSEDED);
        });
    }

    // ── Safety regression: non-PASSIVE_SCAN origin excluded ──────────────────

    @Test
    void nonPassiveScanOpportunityIsExcludedFromPassiveFeed() {
        UUID marketId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        UUID thesisId = UUID.randomUUID();

        // Save a strategy match so the FK constraint is satisfied.
        StrategyMatch match = strategyMatchRepo.save(strategyMatch(marketId, observationId, executionId));

        opportunityRepo.append(new OpportunityFactory().create(new OpportunityFactory.Values(
                new OpportunityId(UUID.randomUUID()), new OpportunityVersion(1),
                OpportunityStatus.ACTIVE, INSTRUMENT, OpportunityDirection.LONG,
                "Bullish breakout", "5m", OpportunityType.SCALPING,
                OpportunityOrigin.ACTIVE_SCAN, // not PASSIVE_SCAN
                new OpportunityScore(BigDecimal.TEN), "Confirmed",
                Set.of(new ObservationReference(observationId)), Set.of(),
                NOW.minusSeconds(10), NOW.minusSeconds(10), NOW.plusSeconds(300), NOW.minusSeconds(10),
                match.matchId(), null, marketId, null, UUID.randomUUID(), marketId, executionId,
                new MarketThesisReference(thesisId, 1))));

        PassiveAwarenessPage page = projectionService.search(
                new PassiveAwarenessQuery(null, null, null, false, 0, 20));

        assertThat(page.total())
                .as("ACTIVE_SCAN opportunity must not appear in the passive feed")
                .isEqualTo(0);
    }

    // ── Scenario G: lease recovery ───────────────────────────────────────────

    @Test
    void expiredLeaseOnDispatchRequestedMarketIsRecoveredToRegistered() {
        UUID runId = UUID.randomUUID();
        UUID scanMarketId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Instant createdAt = NOW.minusSeconds(120);
        Instant leaseExpiredAt = NOW.minusSeconds(1); // lease already expired

        executionRepo.save(minimalExecution(executionId, marketId, "g-exec-" + executionId, createdAt));
        PassiveScanRun run = PassiveScanRun.requested(runId, "key-" + runId, "fp", "scheduler",
                NOW.minusSeconds(120), NOW.minusSeconds(60), "v1", "sha256", "universe-fp",
                createdAt, List.of(), createdAt);
        scanRunRepo.saveRun(run);

        // Persist a DISPATCH_REQUESTED market with an expired lease using restore().
        // analysisExecutionId is required by the domain for all non-SKIPPED statuses;
        // it is pre-assigned at REGISTERED creation (before dispatch).
        PassiveScanMarket market = PassiveScanMarket.restore(
                scanMarketId, runId, 0, marketId, PROVIDER, "SPOT",
                Duration.ofHours(1), TradeabilityEvidenceProfile.UNSUPPORTED,
                PassiveScanMarketStatus.DISPATCH_REQUESTED, executionId,
                1, null, null, null,
                leaseId, leaseExpiredAt, // leaseExpiresAt < NOW → eligible for recovery
                0L, createdAt, createdAt);
        scanRunRepo.saveMarkets(List.of(market));

        boolean recovered = scanRunRepo.recoverExpiredMarket(
                scanMarketId, PassiveScanMarketStatus.DISPATCH_REQUESTED, leaseId, 0L, NOW);
        em.flush();
        em.clear(); // evict Hibernate first-level cache so the reload reflects the JPQL update

        assertThat(recovered).isTrue();
        PassiveScanMarket reloaded = scanRunRepo.findMarketById(scanMarketId).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(PassiveScanMarketStatus.REGISTERED);
        assertThat(reloaded.leaseId()).isNull();
        assertThat(reloaded.leaseExpiresAt()).isNull();
        assertThat(reloaded.version()).isEqualTo(1L);
    }

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private Fixture seedFixture(UUID marketId, UUID executionId) {
        when(marketDataClient.findMarketsByIds(any())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return ids.stream()
                    .map(id -> new MarketResponse(id, PROVIDER, INSTRUMENT, "BTC", "EUR", "SPOT", null))
                    .toList();
        });

        Observation obs = observationRepo.save(qualifyingObservation());
        MarketThesis thesis = thesisRepo.save(
                candidateThesis(UUID.randomUUID(), marketId, executionId,
                        NOW.minusSeconds(10), NOW.plusSeconds(300), null));
        StrategyMatch match = strategyMatchRepo.save(strategyMatch(
                marketId, obs.id(), executionId));
        var opportunity = opportunityRepo.append(opportunityV1(
                marketId, executionId, obs.id(), match.matchId(), thesis.id(), thesis.version()));
        return new Fixture(obs, thesis, match, opportunity);
    }

    private record Fixture(
            Observation observation,
            MarketThesis thesis,
            StrategyMatch match,
            com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity opportunity) {}

    private static Observation qualifyingObservation() {
        RawMarketDataReference raw = new RawMarketDataReference(
                "kraken", INSTRUMENT, "5m", "abc", NOW.minusSeconds(60));
        ArtifactTrace artifactTrace = new ArtifactTrace(
                new ArtifactIdentity("SPREAD", "spread-cap", "v1"), "p-fp", "i-fp", List.of(raw));
        ObservationEvidence evidence = new ObservationEvidence(
                UUID.randomUUID(), "spread-rule", "Spread below threshold",
                "Spread is within acceptable range",
                Map.of("spread", new BigDecimal("0.002")),
                Map.of("max", new BigDecimal("0.005")),
                NOW.minusSeconds(20), new BigDecimal("0.80"),
                new CapabilityResultTrace(UUID.randomUUID(), "spread-cap", "v1",
                        List.of(artifactTrace)));
        return new ObservationFactory().create(
                UUID.randomUUID(), 1, INSTRUMENT, new ObservationType("BREAKOUT"),
                "Breakout detected", "Momentum confirmed with spread check",
                Set.of("structure"), "5m",
                NOW.minusSeconds(20), NOW.minusSeconds(20), NOW.plusSeconds(600),
                null, "rule-v1", List.of(evidence));
    }

    private static MarketThesis candidateThesis(UUID thesisId, UUID marketId, UUID executionId,
            Instant sourceCutoff, Instant validUntil, UUID supersedes) {
        MarketThesisEvidence evidence = new MarketThesisEvidence(
                UUID.randomUUID(), MarketThesisEvidenceRole.SUPPORTING,
                MarketThesisAvailability.AVAILABLE, null,
                "spread-rule", "spread-cap", "5m", "Spread is positive",
                sourceCutoff.minusSeconds(5), validUntil,
                PROVIDER, INSTRUMENT, "fp-v1", "rule-v1", sourceCutoff, true);
        return new MarketThesis(
                thesisId, UUID.randomUUID(), 1, marketId, INSTRUMENT,
                MarketThesisState.CANDIDATE,
                new MarketThesisScenario(MarketThesisScenario.Direction.LONG,
                        "breakout", "Price breaks resistance", "Structure fails", "100"),
                List.of(evidence), List.of(),
                sourceCutoff, sourceCutoff, sourceCutoff, validUntil,
                executionId, "composer-v1", "input-fp-" + thesisId, supersedes);
    }

    private static StrategyMatch strategyMatch(UUID marketId, UUID observationId, UUID executionId) {
        return StrategyMatch.rehydrate(new StrategyMatch.Rehydration(
                UUID.randomUUID(), StrategyId.random(), 1, marketId,
                executionId, observationId, MatchedDirection.LONG,
                "ctx-digest-v1", List.of(), null,
                NOW.minusSeconds(15), NOW.minusSeconds(10)));
    }

    private static com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity opportunityV1(
            UUID marketId, UUID executionId, UUID observationId, UUID matchId,
            UUID thesisId, long thesisVersion) {
        UUID scanRunId = UUID.randomUUID();
        return new OpportunityFactory().create(new OpportunityFactory.Values(
                new OpportunityId(UUID.randomUUID()), new OpportunityVersion(1),
                OpportunityStatus.ACTIVE, INSTRUMENT, OpportunityDirection.LONG,
                "Bullish breakout", "5m", OpportunityType.SCALPING,
                OpportunityOrigin.PASSIVE_SCAN,
                new OpportunityScore(BigDecimal.TEN), "Confirmed",
                Set.of(new ObservationReference(observationId)), Set.of(),
                NOW.minusSeconds(10), NOW.minusSeconds(10), NOW.plusSeconds(300), NOW.minusSeconds(10),
                matchId, null, marketId, null, scanRunId, marketId, executionId,
                new MarketThesisReference(thesisId, thesisVersion)));
    }

    private static com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity opportunityVersion(
            OpportunityId id, long version, UUID marketId, UUID executionId,
            UUID observationId, UUID matchId, UUID thesisId, long thesisVersion,
            Instant evaluatedAt, Instant validUntil) {
        UUID scanRunId = UUID.randomUUID();
        return new OpportunityFactory().create(new OpportunityFactory.Values(
                id, new OpportunityVersion(version),
                OpportunityStatus.ACTIVE, INSTRUMENT, OpportunityDirection.LONG,
                "Bullish breakout", "5m", OpportunityType.SCALPING,
                OpportunityOrigin.PASSIVE_SCAN,
                new OpportunityScore(BigDecimal.TEN), "Confirmed",
                Set.of(new ObservationReference(observationId)), Set.of(),
                evaluatedAt, evaluatedAt, validUntil, evaluatedAt,
                matchId, null, marketId, null, scanRunId, marketId, executionId,
                new MarketThesisReference(thesisId, thesisVersion)));
    }

    private static AnalysisExecution minimalExecution(UUID id, UUID marketId, String key, Instant now) {
        return AnalysisExecution.requested(id, new IdempotencyKey(key),
                new AnalysisExecutionPolicy(Duration.ofMinutes(5), Duration.ofSeconds(30), 0, 1,
                        new ContextLimits(10, 10, 10_000, 5, ContextClassification.PUBLIC),
                        new RetryPolicy(0, Duration.ZERO, java.util.Set.of()), java.util.Map.of(),
                        new DegradationPolicy(true, true, true, true)),
                now, List.of(),
                new AnalysisExecutionProvenance(marketId, AnalysisExecutionMode.PASSIVE, "PASSIVE_SCAN", "v1"),
                new AnalysisTraceMetadata(List.of()));
    }

    // ── Mutable clock for time-controlled assertions ──────────────────────────

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void set(Instant value) { now = value; }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(java.time.ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }
}
