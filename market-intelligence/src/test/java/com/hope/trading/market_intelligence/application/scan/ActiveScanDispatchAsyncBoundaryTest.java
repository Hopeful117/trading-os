package com.hope.trading.market_intelligence.application.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.adapter.persistence.InMemoryAnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.application.strategy.AnalysisStrategyRegistry;
import com.hope.trading.market_intelligence.application.strategy.AnalysisExecutionStrategy;
import com.hope.trading.market_intelligence.domain.scan.ActiveScan;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanMarket;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanMarketStatus;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanStatus;
import com.hope.trading.market_intelligence.domain.scope.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the async dispatch boundary introduced by Story 0025: scan creation
 * must complete without waiting for the dispatch worker, and the worker must
 * run on a thread that is not the caller's. This is the defect class that
 * produced 161 s POSTs and nginx 504s before the fix.
 */
class ActiveScanDispatchAsyncBoundaryTest {

    private final Instant now = Instant.parse("2026-08-25T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ActiveScanRepository scans = new MinimalInMemoryScanRepository();
    private final ActiveScanScopeResolutionService scopeResolution =
            mock(ActiveScanScopeResolutionService.class);
    private final ActiveScanDispatchClaimService claims =
            mock(ActiveScanDispatchClaimService.class);

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createReturnsWhileRealDispatchWorkerIsStillBlocked() throws Exception {
        UUID marketId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        whenScopeResolvesToSingleEligibleMarket(actorId, marketId);

        // Real coordinator, real virtual-thread executor — only the claim is
        // replaced by a blocking stub so the worker visibly stays busy while
        // the caller has already moved on.
        CountDownLatch resumeStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicReference<String> workerThreadName = new AtomicReference<>();
        doAnswer(invocation -> {
            workerThreadName.set(Thread.currentThread().getName());
            resumeStarted.countDown();
            releaseWorker.await(10, TimeUnit.SECONDS);
            return null;
        }).when(claims).claimForDispatch(any(), any());

        ExecutorService scanDispatchExecutor = Executors.newVirtualThreadPerTaskExecutor();
        ActiveScanDispatchCoordinator coordinator = new ActiveScanDispatchCoordinator(
                scans,
                claims,
                mock(AnalysisExecutionService.class),
                scanDispatchExecutor
        );
        ActiveScanApplicationService service = new ActiveScanApplicationService(
                scans,
                scopeResolution,
                realAnalysisExecutionService(),
                new ActiveScanFingerprintFactory(new ObjectMapper()),
                new ActiveScanChildKeyFactory(),
                coordinator,
                mock(ActiveScanReconciliationService.class),
                clock
        );

        TransactionSynchronizationManager.initSynchronization();
        long startNanos = System.nanoTime();
        UUID scanId = service.create(new CreateActiveScanCommand(
                actorId, "boundary-key", UUID.randomUUID(), "scan", null)
        ).scanId();

        // after-commit hook fires synchronously here; with the regression this
        // line blocked for the whole dispatch duration instead of scheduling it.
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        // 1) The worker actually started, on its own thread.
        assertThat(resumeStarted.await(5, TimeUnit.SECONDS))
                .as("dispatch worker should start after commit")
                .isTrue();

        // 2) Creation did NOT wait for dispatch completion: the caller thread
        //    reached this point while the worker is still blocked inside the
        //    very first claim.
        assertThat(workerThreadName.get())
                .isNotEqualTo(Thread.currentThread().getName());

        // 3) The persisted scan exists and can be tracked by its id.
        assertThat(service.findOwned(actorId, scanId)).isNotNull();

        releaseWorker.countDown();
        scanDispatchExecutor.shutdown();
        assertThat(scanDispatchExecutor.awaitTermination(5, TimeUnit.SECONDS))
                .isTrue();
    }

    private AnalysisExecutionService realAnalysisExecutionService() {
        return new AnalysisExecutionService(
                new com.hope.trading.market_intelligence.adapter.persistence
                        .InMemoryAnalysisExecutionRepository(),
                new com.hope.trading.market_intelligence.application.port.AnalysisExecutionDispatcher() {
                    @Override public void dispatch(
                            java.util.UUID executionId,
                            com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest request) { }
                    @Override public void cancel(java.util.UUID executionId) { }
                },
                new AnalysisStrategyRegistry(List.of(activeStrategy())),
                new com.hope.trading.market_intelligence.application.execution
                        .AnalysisExecutionPolicyFactory(),
                clock
        );
    }

    private AnalysisExecutionStrategy activeStrategy() {
        return new AnalysisExecutionStrategy() {
            @Override public com.hope.trading.market_intelligence.domain.AnalysisExecutionMode mode() {
                return com.hope.trading.market_intelligence.domain.AnalysisExecutionMode.ACTIVE;
            }

            @Override public com.hope.trading.market_intelligence.application.strategy
                    .AnalysisExecutionPlan plan(
                    com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest request) {
                return new com.hope.trading.market_intelligence.application.strategy
                        .AnalysisExecutionPlan(List.of("deterministic-active"), List.of(), 3,
                                java.time.Duration.ofSeconds(3));
            }
        };
    }

    private void whenScopeResolvesToSingleEligibleMarket(
            UUID accountId, UUID marketId) {
        when(scopeResolution.resolve(any())).thenReturn(new ActiveScanScopeResolutionResult(
                accountId,
                "scan",
                List.of(marketId),
                List.of(marketId),
                List.of(new MarketEligibilityDecision(
                        marketId, "ACH/EUR", "KRAKEN", true, List.of())),
                new EffectiveScanScope(List.of(marketId)),
                now
        ));
    }

    /** Minimal in-memory persistence; only what this boundary test exercises. */
    private static final class MinimalInMemoryScanRepository
            implements com.hope.trading.market_intelligence.application.port.ActiveScanRepository {
        private final java.util.Map<UUID, ActiveScan> scansById = new java.util.HashMap<>();
        private final java.util.Map<UUID, List<ActiveScanMarket>> marketsByScanId =
                new java.util.HashMap<>();

        @Override public ActiveScan save(ActiveScan scan) {
            scansById.put(scan.scanId(), scan);
            return scan;
        }

        @Override public List<ActiveScanMarket> saveMarkets(List<ActiveScanMarket> markets) {
            markets.forEach(market -> marketsByScanId
                    .computeIfAbsent(market.scanId(), id -> new java.util.ArrayList<>())
                    .add(market));
            return markets;
        }

        @Override public java.util.Optional<ActiveScan> findByActorIdAndIdempotencyKey(
                UUID actorId, String idempotencyKey) {
            return java.util.Optional.empty();
        }

        @Override public java.util.Optional<ActiveScan> findByActorIdAndScanId(
                UUID actorId, UUID scanId) {
            ActiveScan scan = scansById.get(scanId);
            return scan != null && scan.actorId().equals(actorId)
                    ? java.util.Optional.of(scan) : java.util.Optional.empty();
        }

        @Override public java.util.Optional<ActiveScan> findById(UUID scanId) {
            return java.util.Optional.ofNullable(scansById.get(scanId));
        }

        @Override public List<ActiveScanMarket> findMarketsByScanId(UUID scanId) {
            return marketsByScanId.getOrDefault(scanId, List.of());
        }

        @Override public java.util.Optional<ActiveScanMarket> findMarketById(UUID scanMarketId) {
            return marketsByScanId.values().stream()
                    .flatMap(List::stream)
                    .filter(market -> market.scanMarketId().equals(scanMarketId))
                    .findFirst();
        }

        @Override public boolean transitionScanStatus(
                UUID scanId, ActiveScanStatus expected,
                ActiveScanStatus target, Instant updatedAt) {
            return false;
        }

        @Override public boolean transitionMarketStatus(
                UUID scanMarketId, ActiveScanMarketStatus expected,
                ActiveScanMarketStatus target, Instant updatedAt) {
            return false;
        }
    }
}
