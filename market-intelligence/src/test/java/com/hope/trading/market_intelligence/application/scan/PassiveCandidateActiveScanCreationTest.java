package com.hope.trading.market_intelligence.application.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.adapter.persistence.InMemoryAnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionPolicyFactory;
import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.passive.PassiveCandidateFixtures;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionDispatcher;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.application.strategy.ActiveAnalysisStrategy;
import com.hope.trading.market_intelligence.application.strategy.AnalysisStrategyRegistry;
import com.hope.trading.market_intelligence.application.strategy.PassiveAnalysisStrategy;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin;
import com.hope.trading.market_intelligence.domain.scan.*;
import com.hope.trading.market_intelligence.domain.scope.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static com.hope.trading.market_intelligence.application.passive.PassiveCandidateFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Story 0243 end-to-end creation path with deterministic fixtures: real application
 * service, creation transaction, evaluator and validator; mocked scope resolution and
 * Market Data; no broker, account sync, AI or TradePlan collaborators exist here at all.
 */
class PassiveCandidateActiveScanCreationTest {
    private final PassiveCandidateFixtures fixtures = new PassiveCandidateFixtures();
    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryScans scans = new InMemoryScans();
    private final RecordingDispatcher dispatcher = new RecordingDispatcher();
    private final AnalysisExecutionService executions = new AnalysisExecutionService(
            new InMemoryAnalysisExecutionRepository(), dispatcher,
            new AnalysisStrategyRegistry(List.of(new ActiveAnalysisStrategy(Duration.ofSeconds(3)))),
            new AnalysisExecutionPolicyFactory(), clock);
    private final ActiveScanScopeResolutionService scopeResolution = mock(ActiveScanScopeResolutionService.class);
    private final PassiveCandidateScanValidator validator = new PassiveCandidateScanValidator(
            fixtures.evaluator(), mock(AnalysisExecutionRepository.class),
            new PassiveAnalysisStrategy(Duration.ofMillis(750)), new ActiveAnalysisStrategy(Duration.ofSeconds(3)));
    private final ActiveScanApplicationService service = new ActiveScanApplicationService(
            scans, new ActiveScanFingerprintFactory(new ObjectMapper()),
            new ActiveScanCreationTransaction(scans, scopeResolution, executions, new ActiveScanChildKeyFactory(),
                    mock(ActiveScanDispatchCoordinator.class), clock, validator),
            mock(ActiveScanReconciliationService.class));
    private final UUID actor = UUID.randomUUID();
    private final UUID account = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void validCandidateCreatesActiveScanWithImmutableProvenanceAndLiveEvidence() {
        eligible(true);
        TransactionSynchronizationManager.initSynchronization();

        ActiveScan scan = service.create(command("key-1", fixtures.opportunity.version().value()));

        assertThat(scan.status()).isEqualTo(ActiveScanStatus.READY_TO_DISPATCH);
        assertThat(scan.passiveCandidate()).hasValueSatisfying(provenance -> {
            assertThat(provenance.opportunityId()).isEqualTo(fixtures.opportunity.id().value());
            assertThat(provenance.opportunityVersion()).isEqualTo(1);
            assertThat(provenance.marketId()).isEqualTo(fixtures.marketId);
            assertThat(provenance.validatedAt()).isEqualTo(NOW);
        });
        assertThat(scans.findMarketsByScanId(scan.scanId())).singleElement()
                .satisfies(market -> assertThat(market.analysisExecutionId()).isNotNull());
        var registered = executions.find(
                scans.findMarketsByScanId(scan.scanId()).getFirst().analysisExecutionId()).provenance();
        assertThat(registered.evidencePolicy()).isEqualTo(EvidenceAcquisitionPolicy.LIVE_CURRENT);
        assertThat(registered.mode()).isEqualTo(com.hope.trading.market_intelligence.domain.AnalysisExecutionMode.ACTIVE);
        assertThat(registered.accountId()).isEqualTo(account);
        verifyNoPassiveSourceWrites();
    }

    @Test
    void invalidCandidatePersistsNothingAndRegistersNoExecution() {
        eligible(true);
        fixtures.opportunity = fixtures.opportunity(1, OpportunityOrigin.PASSIVE_SCAN, null, NOW.minusSeconds(1));
        fixtures.install();

        assertThatThrownBy(() -> service.create(command("key-1", 1)))
                .isInstanceOfSatisfying(ActiveScanException.class,
                        failure -> assertThat(failure.code()).isEqualTo("PASSIVE_CANDIDATE_NOT_CURRENT"));
        assertThat(scans.all()).isEmpty();
        verify(scopeResolution, never()).resolve(any());
        verifyNoPassiveSourceWrites();
    }

    @Test
    void replayReturnsOriginalScanWithoutRevalidationEvenAfterExpiry() {
        eligible(true);
        TransactionSynchronizationManager.initSynchronization();
        ActiveScan first = service.create(command("key-1", 1));
        clock.set(NOW.plusSeconds(3_600));

        ActiveScan replay = service.create(command("key-1", 1));

        assertThat(replay.scanId()).isEqualTo(first.scanId());
        assertThat(scans.all()).hasSize(1);
        verify(fixtures.opportunities, times(1)).find(any(), any());
    }

    @Test
    void differentCandidateVersionUnderSameKeyConflicts() {
        eligible(true);
        TransactionSynchronizationManager.initSynchronization();
        service.create(command("key-1", 1));

        assertThatThrownBy(() -> service.create(command("key-1", 2)))
                .isInstanceOfSatisfying(ActiveScanException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
                    assertThat(failure.status()).isEqualTo(409);
                });
    }

    @Test
    void legacyRequestWithSameKeyAsCandidateRequestConflicts() {
        eligible(true);
        TransactionSynchronizationManager.initSynchronization();
        service.create(command("key-1", 1));

        assertThatThrownBy(() -> service.create(new CreateActiveScanCommand(actor, "key-1", account, null,
                "investigate", List.of(fixtures.marketId), MarketScopeMode.SELECTED,
                TradeabilityEvidenceProfile.UNSUPPORTED)))
                .isInstanceOfSatisfying(ActiveScanException.class,
                        failure -> assertThat(failure.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void accountIneligibleForMarketIsTruthfulNoWorkWithProvenanceRetained() {
        eligible(false);

        ActiveScan scan = service.create(command("key-1", 1));

        assertThat(scan.status()).isEqualTo(ActiveScanStatus.COMPLETED_NO_WORK);
        assertThat(scan.passiveCandidate()).isPresent();
        assertThat(dispatcher.dispatched).isZero();
    }

    @Test
    void accountOwnershipFailureFromExistingScopeResolutionPropagates() {
        when(scopeResolution.resolve(any())).thenThrow(
                com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionException
                        .notFound("Account is not available"));

        assertThatThrownBy(() -> service.create(command("key-1", 1)))
                .isInstanceOf(com.hope.trading.market_intelligence.application.scope
                        .ActiveScanScopeResolutionException.class);
        assertThat(scans.all()).isEmpty();
    }

    @Test
    void incompleteReferenceIsRejectedBeforeAnyWork() {
        assertThatThrownBy(() -> new CreateActiveScanCommand(actor, "key-1", account, null, "investigate",
                List.of(fixtures.marketId), MarketScopeMode.SELECTED, TradeabilityEvidenceProfile.UNSUPPORTED,
                fixtures.opportunity.id().value(), null))
                .isInstanceOfSatisfying(ActiveScanException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("PASSIVE_CANDIDATE_REFERENCE_INCOMPLETE");
                    assertThat(failure.status()).isEqualTo(400);
                });
    }

    @Test
    void legacyWiringWithoutValidatorFailsClosedForCandidateRequests() {
        eligible(true);
        ActiveScanApplicationService legacy = new ActiveScanApplicationService(
                scans, new ActiveScanFingerprintFactory(new ObjectMapper()),
                new ActiveScanCreationTransaction(scans, scopeResolution, executions,
                        new ActiveScanChildKeyFactory(), mock(ActiveScanDispatchCoordinator.class), clock),
                mock(ActiveScanReconciliationService.class));

        assertThatThrownBy(() -> legacy.create(command("key-1", 1)))
                .isInstanceOfSatisfying(ActiveScanException.class,
                        failure -> assertThat(failure.code()).isEqualTo("PASSIVE_CANDIDATE_VALIDATION_UNAVAILABLE"));
        assertThat(scans.all()).isEmpty();
    }

    private void verifyNoPassiveSourceWrites() {
        verify(fixtures.opportunities, never()).append(any());
        verify(fixtures.theses, never()).save(any());
        verify(fixtures.theses, never()).saveIfAbsent(any());
    }

    private CreateActiveScanCommand command(String key, long version) {
        return new CreateActiveScanCommand(actor, key, account, null, "investigate", List.of(fixtures.marketId),
                MarketScopeMode.SELECTED, TradeabilityEvidenceProfile.UNSUPPORTED,
                fixtures.opportunity.id().value(), version);
    }

    private void eligible(boolean eligible) {
        when(scopeResolution.resolve(any())).thenAnswer(invocation -> new ActiveScanScopeResolutionResult(
                account, "investigate", List.of(fixtures.marketId), List.of(fixtures.marketId),
                List.of(new MarketEligibilityDecision(fixtures.marketId, "BTC/EUR", "KRAKEN", eligible,
                        eligible ? List.of() : List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE))),
                new EffectiveScanScope(eligible ? List.of(fixtures.marketId) : List.of()),
                clock.instant()));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void set(Instant value) { now = value; }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(java.time.ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }

    private static final class RecordingDispatcher implements AnalysisExecutionDispatcher {
        private int dispatched;

        @Override public void dispatch(UUID executionId, IntelligenceAnalysisRequest request) { dispatched++; }

        @Override public void cancel(UUID executionId) {
            // Not exercised: creation never cancels.
        }
    }

    private static final class InMemoryScans implements ActiveScanRepository {
        private final Map<UUID, ActiveScan> scans = new LinkedHashMap<>();
        private final Map<UUID, ActiveScanMarket> markets = new LinkedHashMap<>();

        Collection<ActiveScan> all() { return scans.values(); }

        @Override public ActiveScan save(ActiveScan scan) { scans.put(scan.scanId(), scan); return scan; }

        @Override public List<ActiveScanMarket> saveMarkets(List<ActiveScanMarket> values) {
            values.forEach(value -> markets.put(value.scanMarketId(), value));
            return values;
        }

        @Override public Optional<ActiveScan> findByActorIdAndIdempotencyKey(UUID actorId, String key) {
            return scans.values().stream()
                    .filter(scan -> scan.actorId().equals(actorId) && scan.idempotencyKey().equals(key))
                    .findFirst();
        }

        @Override public Optional<ActiveScan> findByActorIdAndScanId(UUID actorId, UUID scanId) {
            return Optional.ofNullable(scans.get(scanId)).filter(scan -> scan.actorId().equals(actorId));
        }

        @Override public Optional<ActiveScan> findById(UUID scanId) { return Optional.ofNullable(scans.get(scanId)); }

        @Override public List<ActiveScan> findRecentByActorId(UUID actorId, int limit) {
            return scans.values().stream().filter(scan -> scan.actorId().equals(actorId)).limit(limit).toList();
        }

        @Override public List<ActiveScanMarket> findMarketsByScanId(UUID scanId) {
            return markets.values().stream().filter(market -> market.scanId().equals(scanId)).toList();
        }

        @Override public Optional<ActiveScanMarket> findMarketById(UUID scanMarketId) {
            return Optional.ofNullable(markets.get(scanMarketId));
        }

        @Override public boolean transitionScanStatus(UUID scanId, ActiveScanStatus expected,
                                                      ActiveScanStatus target, Instant updatedAt) {
            return false;
        }

        @Override public boolean transitionMarketStatus(UUID scanMarketId, ActiveScanMarketStatus expected,
                                                        ActiveScanMarketStatus target, Instant updatedAt) {
            return false;
        }
    }
}
