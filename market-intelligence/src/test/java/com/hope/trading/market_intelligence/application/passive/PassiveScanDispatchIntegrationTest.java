package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketFactStatus;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketFactsResponse;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.adapter.marketdata.OhlcInterval;
import com.hope.trading.market_intelligence.adapter.persistence.JpaAnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionDispatcher;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.application.port.PassiveScanRunRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.IntelligenceExecutionStatus;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactIdentity;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionPolicy;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionProvenance;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.execution.AnalysisTraceMetadata;
import com.hope.trading.market_intelligence.domain.execution.ContextLimits;
import com.hope.trading.market_intelligence.domain.execution.DegradationPolicy;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.execution.RetryPolicy;
import com.hope.trading.market_intelligence.domain.observation.ArtifactTrace;
import com.hope.trading.market_intelligence.domain.observation.CapabilityResultTrace;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationEvidence;
import com.hope.trading.market_intelligence.domain.observation.ObservationFactory;
import com.hope.trading.market_intelligence.domain.observation.ObservationType;
import com.hope.trading.market_intelligence.domain.observation.RawMarketDataReference;
import com.hope.trading.market_intelligence.domain.opportunity.MarketThesisReference;
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
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisAvailability;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidenceRole;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisScenario;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisState;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;

/**
 * Story 0246 — Integration test validating PassiveScanDispatchCoordinator.dispatchOnce()
 * end-to-end against a real Testcontainers PostgreSQL database with Flyway migration.
 *
 * <p>Covers AC-01 through AC-06 (dispatch, persistence, and projection pipeline).
 * Safety: scheduler replaced with mock (AC-04), no broker contact, no live provider calls.
 * Temporal assertions controlled via mocked Clock (AC-06).</p>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PassiveScanDispatchIntegrationTest {

    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private static final String INSTRUMENT = "BTC/EUR";
    private static final String PROVIDER = "KRAKEN";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mi_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Override H2 datasource from application-test.properties with Testcontainers PostgreSQL.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        // Enable coordinator's internal guard (passive-dispatch.enabled must be true).
        registry.add("market-intelligence.passive-dispatch.enabled", () -> "true");
        // Passive universe: one KRAKEN/SPOT market entry for MARKET_ID.
        registry.add("market-intelligence.passive-universe.entries[0].marketId", MARKET_ID::toString);
        registry.add("market-intelligence.passive-universe.entries[0].provider", () -> "KRAKEN");
        registry.add("market-intelligence.passive-universe.entries[0].productType", () -> "SPOT");
        registry.add("market-intelligence.passive-universe.entries[0].cadence", () -> "PT1H");
        registry.add("market-intelligence.passive-universe.entries[0].minimumEvidenceProfile", () -> "UNSUPPORTED");
        registry.add("market-intelligence.passive-universe.entries[0].enabled", () -> "true");
    }

    @Autowired PassiveScanDispatchCoordinator coordinator;
    @Autowired PassiveScanRunRepository scanRunRepo;
    @Autowired JpaAnalysisExecutionRepository executionRepo;
    @Autowired TradingOpportunityRepository opportunityRepo;
    @Autowired ObservationRepository observationRepo;
    @Autowired MarketThesisRepository thesisRepo;
    @Autowired StrategyMatchRepository strategyMatchRepo;
    @Autowired PassiveAwarenessProjectionService projectionService;
    @Autowired DataSource dataSource;

    @MockitoBean MarketDataClient marketDataClient;
    @MockitoBean AnalysisExecutionDispatcher analysisExecutionDispatcher;
    // Scheduler bean is present because passive-dispatch.enabled=true; replaced with a no-op
    // mock so the @Scheduled method is never triggered during test execution (AC-04).
    @MockitoBean PassiveScanDispatchScheduler passiveScanDispatchScheduler;
    @MockitoBean Clock clock;

    @BeforeEach
    void setup() {
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        lenient().when(clock.withZone(any())).thenReturn(clock);

        MarketResponse.MarketStateResponse openState =
                new MarketResponse.MarketStateResponse("OPEN", true, null, NOW.minusSeconds(60));
        MarketResponse qualifyingMarket =
                new MarketResponse(MARKET_ID, PROVIDER, INSTRUMENT, "BTC", "EUR", "SPOT", openState);
        lenient().when(marketDataClient.findAllMarkets()).thenReturn(List.of(qualifyingMarket));
        lenient().when(marketDataClient.findMarketsByIds(any())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return ids.stream()
                    .filter(MARKET_ID::equals)
                    .map(id -> new MarketResponse(id, PROVIDER, INSTRUMENT, "BTC", "EUR", "SPOT", null))
                    .toList();
        });
        lenient().when(marketDataClient.findMarketFacts(any(), any(), anyLong(), anyInt(), anyInt(), anyLong()))
                .thenReturn(eligibleFacts());
    }

    @AfterEach
    void cleanup() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            try (var stmt = conn.createStatement()) {
                // CASCADE handles passive_scan_markets → passive_scan_market_attempts,
                // passive_dispatch_capacity_leases, strategy_matches, etc.
                stmt.execute(
                        "TRUNCATE passive_scan_runs, analysis_executions, observations, " +
                        "market_theses, trading_opportunity_versions CASCADE");
                stmt.execute("UPDATE passive_dispatch_capacity_state SET active_count = 0");
            }
        }
    }

    // ── AC-02 (Scenario A) — qualifying market produces a current candidate ──

    @Test
    void scenarioA_qualifyingMarket_dispatchCreatesRunAndProducesCurrentCandidate() {
        AtomicReference<UUID> capturedExecutionId = new AtomicReference<>();
        doAnswer(inv -> {
            capturedExecutionId.set(inv.getArgument(0));
            return null;
        }).when(analysisExecutionDispatcher).dispatch(any(), any());

        PassiveScanDispatchCoordinator.DispatchResult result = coordinator.dispatchOnce();

        assertThat(result.dispatched()).isTrue();
        assertThat(result.marketCount()).isEqualTo(1);

        UUID executionId = capturedExecutionId.get();
        assertThat(executionId).as("dispatcher was called with an execution ID").isNotNull();

        // PassiveScanRun and PassiveScanMarket were persisted (AC-02).
        PassiveScanMarket scanMarket = scanRunRepo.findLatestMarketByMarketId(MARKET_ID)
                .orElseThrow(() -> new AssertionError("PassiveScanMarket not found for MARKET_ID"));
        assertThat(scanMarket.marketId()).isEqualTo(MARKET_ID);
        assertThat(scanRunRepo.findById(scanMarket.runId()))
                .as("PassiveScanRun referenced by scan market must exist")
                .isPresent();

        // AnalysisExecution was registered (AC-02).
        AnalysisExecution registered = executionRepo.findById(executionId)
                .orElseThrow(() -> new AssertionError("AnalysisExecution not found: " + executionId));
        assertThat(registered.provenance().marketId()).isEqualTo(MARKET_ID);

        // Complete the execution via the required state machine path:
        // ACCEPTED → CONTEXT_BUILDING → RUNNING → COMPLETED (AC-02).
        executionRepo.transitionStatus(executionId,
                AnalysisExecutionStatus.ACCEPTED, AnalysisExecutionStatus.CONTEXT_BUILDING, NOW);
        executionRepo.transitionStatus(executionId,
                AnalysisExecutionStatus.CONTEXT_BUILDING, AnalysisExecutionStatus.RUNNING, NOW);
        AnalysisExecution running = executionRepo.findById(executionId).orElseThrow();
        var intelligence = new ConsolidatedIntelligence(
                executionId, MARKET_ID, AnalysisExecutionMode.PASSIVE,
                IntelligenceExecutionStatus.COMPLETE, List.of(), List.of(), List.of(), null);
        executionRepo.save(running.complete(intelligence, AnalysisResultQuality.COMPLETE, NOW));

        // Seed observation, thesis, strategy match and opportunity for projection (AC-02).
        seedOpportunityFixture(MARKET_ID, executionId);

        PassiveAwarenessPage page = projectionService.search(
                new PassiveAwarenessQuery(null, null, null, true, 0, 20));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.current()).isTrue();
            assertThat(item.diagnostics()).isEmpty();
        });
    }

    // ── AC-03 (Scenario B) — quiet market produces no current candidate ───────

    @Test
    void scenarioB_quietMarket_noOpportunity_producesNoCandidates() {
        doNothing().when(analysisExecutionDispatcher).dispatch(any(), any());

        coordinator.dispatchOnce();

        PassiveAwarenessPage page = projectionService.search(
                new PassiveAwarenessQuery(null, null, null, true, 0, 20));
        assertThat(page.total()).as("quiet market produces no current candidates").isEqualTo(0);
    }

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private void seedOpportunityFixture(UUID marketId, UUID executionId) {
        Observation obs = observationRepo.save(qualifyingObservation());
        MarketThesis thesis = thesisRepo.save(candidateThesis(UUID.randomUUID(), marketId,
                executionId, NOW.minusSeconds(10), NOW.plusSeconds(300), null));
        StrategyMatch match = strategyMatchRepo.save(strategyMatch(marketId, obs.id(), executionId));
        opportunityRepo.append(opportunityV1(marketId, executionId, obs.id(),
                match.matchId(), thesis.id(), thesis.version()));
    }

    private static MarketFactsResponse eligibleFacts() {
        MarketFactsResponse.MarketActivityResponse activity = new MarketFactsResponse.MarketActivityResponse(
                MARKET_ID, PROVIDER, INSTRUMENT, "BTC", "EUR",
                OhlcInterval.ONE_HOUR, Duration.ofMinutes(60),
                NOW.minusSeconds(3600), NOW.minusSeconds(60), NOW.minusSeconds(60),
                Duration.ofMinutes(5),
                new BigDecimal("1000000"),
                20, 20, 20, 0, 0, 0,
                MarketFactStatus.AVAILABLE, null, "v1");
        MarketFactsResponse.MarketReadinessResponse readiness = new MarketFactsResponse.MarketReadinessResponse(
                MARKET_ID, OhlcInterval.ONE_HOUR,
                24, 20, 12,
                20,         // observedCompletedCandles >= minimumCompletedCandles (12) ✓
                20, 0, 0, 0, 0, 0,  // normalized; no synthetic/missing/cadence/duplicate/conflicting
                NOW.minusSeconds(3600), NOW.minusSeconds(60), NOW.minusSeconds(60),
                Duration.ofMinutes(5),
                MarketFactStatus.AVAILABLE, null, "v1");
        return new MarketFactsResponse(MARKET_ID, INSTRUMENT, "BTC", "EUR",
                NOW.minusSeconds(60), activity, readiness);
    }

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
        return new OpportunityFactory().create(new OpportunityFactory.Values(
                new OpportunityId(UUID.randomUUID()), new OpportunityVersion(1),
                OpportunityStatus.ACTIVE, INSTRUMENT, OpportunityDirection.LONG,
                "Bullish breakout", "5m", OpportunityType.SCALPING,
                OpportunityOrigin.PASSIVE_SCAN,
                new OpportunityScore(BigDecimal.TEN), "Confirmed",
                Set.of(new ObservationReference(observationId)), Set.of(),
                NOW.minusSeconds(10), NOW.minusSeconds(10), NOW.plusSeconds(300), NOW.minusSeconds(10),
                matchId, null, marketId, null, UUID.randomUUID(), marketId, executionId,
                new MarketThesisReference(thesisId, thesisVersion)));
    }
}
