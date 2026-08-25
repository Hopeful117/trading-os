package com.hope.trading.market_intelligence.application.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.adapter.persistence.InMemoryAnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.application.strategy.AnalysisExecutionPlan;
import com.hope.trading.market_intelligence.application.strategy.AnalysisExecutionStrategy;
import com.hope.trading.market_intelligence.application.strategy.AnalysisStrategyRegistry;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.scan.ActiveScan;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanMarket;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanStatus;
import com.hope.trading.market_intelligence.domain.scope.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActiveScanApplicationServiceTest {
    private final Instant now = Instant.parse("2026-08-20T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ActiveScanRepository scans = new InMemoryActiveScanRepository();
    private final CountingDispatcher dispatcher = new CountingDispatcher();
    private final AnalysisExecutionService executions = new AnalysisExecutionService(
            new InMemoryAnalysisExecutionRepository(),
            dispatcher,
            new AnalysisStrategyRegistry(List.of(activeStrategy())),
            new com.hope.trading.market_intelligence.application.execution.AnalysisExecutionPolicyFactory(),
            clock
    );
    private final ActiveScanFingerprintFactory fingerprints =
            new ActiveScanFingerprintFactory(new ObjectMapper());
    private final ActiveScanChildKeyFactory childKeys = new ActiveScanChildKeyFactory();
    private final ActiveScanScopeResolutionService scopeResolution = mock(ActiveScanScopeResolutionService.class);
    private final ActiveScanDispatchCoordinator coordinator = mock(ActiveScanDispatchCoordinator.class);
    private final ActiveScanReconciliationService reconciliation = mock(ActiveScanReconciliationService.class);
    private final ActiveScanApplicationService service = new ActiveScanApplicationService(
            scans,
            scopeResolution,
            executions,
            fingerprints,
            childKeys,
            coordinator,
            reconciliation,
            clock
    );

    @AfterEach
    void cleanupSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createPersistsMixedScopeAndDefersDispatchUntilAfterCommit() {
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID eligible = UUID.randomUUID();
        UUID excluded = UUID.randomUUID();
        when(scopeResolution.resolve(any())).thenReturn(new ActiveScanScopeResolutionResult(
                accountId,
                "scan",
                List.of(eligible, excluded),
                List.of(eligible, excluded),
                List.of(
                        new MarketEligibilityDecision(eligible, "ACH/EUR", "KRAKEN", true, List.of()),
                        new MarketEligibilityDecision(excluded, "AI3/EUR", "KRAKEN", false,
                                List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE))
                ),
                new EffectiveScanScope(List.of(eligible)),
                now
        ));

        TransactionSynchronizationManager.initSynchronization();
        AnalysisExecution executionBefore = executions.register(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), UUID.randomUUID(),
                        AnalysisExecutionMode.ACTIVE, "other"),
                new IdempotencyKey("different"),
                "req",
                "trace"
        );
        ActiveScanApplicationService.ActiveScanView created = service.findOwned(
                actorId,
                service.create(new CreateActiveScanCommand(
                        actorId,
                        "scan-key",
                        accountId,
                        "scan",
                        List.of(eligible, excluded)
                )).scanId()
        );

        assertThat(created.scan().status()).isEqualTo(ActiveScanStatus.READY_TO_DISPATCH);
        assertThat(created.markets()).hasSize(2);
        assertThat(created.markets()).filteredOn(market -> !market.eligible())
                .singleElement()
                .satisfies(market -> assertThat(market.analysisExecutionId()).isNull());
        verify(coordinator, never()).resume(any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        verify(coordinator, timeout(2000)).resumeAsync(created.scan().scanId());
        assertThat(executionBefore.executionId()).isNotNull();
    }

    @Test
    void replayWithSameActorKeyAndFingerprintReusesSameScan() {
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        when(scopeResolution.resolve(any())).thenReturn(new ActiveScanScopeResolutionResult(
                accountId,
                "scan",
                List.of(marketId),
                List.of(marketId),
                List.of(new MarketEligibilityDecision(marketId, "ACH/EUR", "KRAKEN", true, List.of())),
                new EffectiveScanScope(List.of(marketId)),
                now
        ));

        TransactionSynchronizationManager.initSynchronization();
        UUID first = service.create(new CreateActiveScanCommand(
                actorId, "scan-key", accountId, "scan", List.of(marketId)
        )).scanId();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        UUID second = service.create(new CreateActiveScanCommand(
                actorId, "scan-key", accountId, "scan", List.of(marketId)
        )).scanId();

        assertThat(second).isEqualTo(first);
        verify(scopeResolution, times(1)).resolve(any());
    }

    @Test
    void replayWithSameActorKeyButDifferentRequestConflicts() {
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        when(scopeResolution.resolve(any())).thenReturn(new ActiveScanScopeResolutionResult(
                accountId,
                "scan",
                List.of(marketId),
                List.of(marketId),
                List.of(new MarketEligibilityDecision(marketId, "ACH/EUR", "KRAKEN", true, List.of())),
                new EffectiveScanScope(List.of(marketId)),
                now
        ));

        TransactionSynchronizationManager.initSynchronization();
        service.create(new CreateActiveScanCommand(
                actorId, "scan-key", accountId, "scan", List.of(marketId)
        ));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> service.create(new CreateActiveScanCommand(
                actorId, "scan-key", accountId, "other", List.of(marketId)
        ))).isInstanceOf(ActiveScanException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void sameRawKeyUnderDifferentActorsCreatesIndependentScans() {
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        when(scopeResolution.resolve(any())).thenReturn(new ActiveScanScopeResolutionResult(
                accountId,
                "scan",
                List.of(marketId),
                List.of(marketId),
                List.of(new MarketEligibilityDecision(marketId, "ACH/EUR", "KRAKEN", true, List.of())),
                new EffectiveScanScope(List.of(marketId)),
                now
        ));

        TransactionSynchronizationManager.initSynchronization();
        UUID first = service.create(new CreateActiveScanCommand(
                UUID.randomUUID(), "scan-key", accountId, "scan", List.of(marketId)
        )).scanId();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        UUID second = service.create(new CreateActiveScanCommand(
                UUID.randomUUID(), "scan-key", accountId, "scan", List.of(marketId)
        )).scanId();

        assertThat(second).isNotEqualTo(first);
        verify(scopeResolution, times(2)).resolve(any());
    }

    @Test
    void emptyEffectiveScopeProducesCompletedNoWork() {
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        when(scopeResolution.resolve(any())).thenReturn(new ActiveScanScopeResolutionResult(
                accountId,
                "scan",
                List.of(marketId),
                List.of(marketId),
                List.of(new MarketEligibilityDecision(
                        marketId, "AI3/EUR", "KRAKEN", false, List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE)
                )),
                new EffectiveScanScope(List.of()),
                now
        ));

        TransactionSynchronizationManager.initSynchronization();
        ActiveScanApplicationService.ActiveScanView created = service.findOwned(
                actorId,
                service.create(new CreateActiveScanCommand(
                        actorId, "scan-key", accountId, "scan", List.of(marketId)
                )).scanId()
        );

        assertThat(created.scan().status()).isEqualTo(ActiveScanStatus.COMPLETED_NO_WORK);
        assertThat(created.markets()).singleElement().satisfies(market ->
                assertThat(market.analysisExecutionId()).isNull());
        verify(coordinator, never()).resume(any());
    }

    private AnalysisExecutionStrategy activeStrategy() {
        return new AnalysisExecutionStrategy() {
            @Override
            public AnalysisExecutionMode mode() {
                return AnalysisExecutionMode.ACTIVE;
            }

            @Override
            public AnalysisExecutionPlan plan(IntelligenceAnalysisRequest request) {
                return new AnalysisExecutionPlan(
                        List.of("deterministic-active"),
                        List.of(),
                        3,
                        Duration.ofSeconds(3)
                );
            }
        };
    }

    private static final class CountingDispatcher
            implements com.hope.trading.market_intelligence.application.port.AnalysisExecutionDispatcher {
        private final AtomicInteger dispatches = new AtomicInteger();

        @Override
        public void dispatch(UUID executionId, IntelligenceAnalysisRequest request) {
            dispatches.incrementAndGet();
        }

        @Override
        public void cancel(UUID executionId) {
        }
    }

    private static final class InMemoryActiveScanRepository implements ActiveScanRepository {
        private final Map<UUID, com.hope.trading.market_intelligence.domain.scan.ActiveScan> scans = new LinkedHashMap<>();
        private final Map<UUID, ActiveScanMarket> markets = new LinkedHashMap<>();

        @Override
        public com.hope.trading.market_intelligence.domain.scan.ActiveScan save(
                com.hope.trading.market_intelligence.domain.scan.ActiveScan scan
        ) {
            scans.put(scan.scanId(), scan);
            return scan;
        }

        @Override
        public List<ActiveScanMarket> saveMarkets(List<ActiveScanMarket> values) {
            values.forEach(market -> markets.put(market.scanMarketId(), market));
            return values;
        }

        @Override
        public Optional<com.hope.trading.market_intelligence.domain.scan.ActiveScan> findByActorIdAndIdempotencyKey(
                UUID actorId,
                String idempotencyKey
        ) {
            return scans.values().stream()
                    .filter(scan -> scan.actorId().equals(actorId))
                    .filter(scan -> scan.idempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override
        public Optional<com.hope.trading.market_intelligence.domain.scan.ActiveScan> findByActorIdAndScanId(
                UUID actorId,
                UUID scanId
        ) {
            return findById(scanId).filter(scan -> scan.actorId().equals(actorId));
        }

        @Override
        public Optional<com.hope.trading.market_intelligence.domain.scan.ActiveScan> findById(UUID scanId) {
            return Optional.ofNullable(scans.get(scanId));
        }

        @Override
        public List<ActiveScanMarket> findMarketsByScanId(UUID scanId) {
            return markets.values().stream()
                    .filter(market -> market.scanId().equals(scanId))
                    .sorted(Comparator.comparingInt(ActiveScanMarket::ordinal))
                    .toList();
        }

        @Override
        public Optional<ActiveScanMarket> findMarketById(UUID scanMarketId) {
            return Optional.ofNullable(markets.get(scanMarketId));
        }

        @Override
        public boolean transitionScanStatus(
                UUID scanId,
                ActiveScanStatus expected,
                ActiveScanStatus target,
                Instant updatedAt
        ) {
            com.hope.trading.market_intelligence.domain.scan.ActiveScan current = scans.get(scanId);
            if (current == null || current.status() != expected) {
                return false;
            }
            scans.put(scanId, current.reconcileTo(target, updatedAt));
            return true;
        }

        @Override
        public boolean transitionMarketStatus(
                UUID scanMarketId,
                com.hope.trading.market_intelligence.domain.scan.ActiveScanMarketStatus expected,
                com.hope.trading.market_intelligence.domain.scan.ActiveScanMarketStatus target,
                Instant updatedAt
        ) {
            ActiveScanMarket current = markets.get(scanMarketId);
            if (current == null || current.status() != expected || target != com.hope.trading.market_intelligence.domain.scan.ActiveScanMarketStatus.DISPATCH_REQUESTED) {
                return false;
            }
            markets.put(scanMarketId, current.markDispatchRequested(updatedAt));
            return true;
        }
    }
}
