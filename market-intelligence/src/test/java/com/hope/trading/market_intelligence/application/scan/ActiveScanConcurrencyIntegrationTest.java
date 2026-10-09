package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.adapter.persistence.JpaAnalysisExecutionRepository;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.execution.*;
import com.hope.trading.market_intelligence.domain.scan.*;
import com.hope.trading.market_intelligence.domain.scope.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ActiveScanConcurrencyIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Autowired
    private ActiveScanApplicationService scans;

    @Autowired
    private ActiveScanRepository scanRepository;

    @Autowired
    private ActiveScanDispatchClaimService claims;

    @Autowired
    private JpaAnalysisExecutionRepository executions;

    @MockitoBean
    private ActiveScanScopeResolutionService scopeResolution;

    @Test
    void concurrentSameRequestCreatesOneScanAndOneChild() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        when(scopeResolution.resolve(any())).thenReturn(resolved(accountId, marketId));

        CreateActiveScanCommand command = new CreateActiveScanCommand(
                actorId, "concurrent-scan-key", accountId, "scan", List.of(marketId), MarketScopeMode.SELECTED
        );
        List<ActiveScan> results = concurrently(
                () -> scans.create(command),
                () -> scans.create(command)
        );

        assertThat(results.get(0).scanId()).isEqualTo(results.get(1).scanId());
        ActiveScanApplicationService.ActiveScanView stored = scans.findOwned(actorId, results.get(0).scanId());
        assertThat(stored.markets()).singleElement().extracting(ActiveScanMarket::analysisExecutionId)
                .isNotNull();
        assertThat(scanRepository.findByActorIdAndIdempotencyKey(actorId, command.idempotencyKey()))
                .hasValueSatisfying(scan -> assertThat(scan.scanId()).isEqualTo(results.get(0).scanId()));
    }

    @Test
    void concurrentDifferentRequestWithSameKeyReturnsConflict() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        when(scopeResolution.resolve(any())).thenReturn(emptyResolved(accountId, marketId));

        CreateActiveScanCommand first = new CreateActiveScanCommand(
                actorId, "conflicting-key", accountId, "first", List.of(marketId), MarketScopeMode.SELECTED
        );
        CreateActiveScanCommand second = new CreateActiveScanCommand(
                actorId, "conflicting-key", accountId, "second", List.of(marketId), MarketScopeMode.SELECTED
        );
        List<Outcome> outcomes = concurrently(
                () -> outcome(() -> scans.create(first)),
                () -> outcome(() -> scans.create(second))
        );

        assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.success()).singleElement()
                .satisfies(outcome -> assertThat(outcome.error()).isInstanceOfSatisfying(
                        ActiveScanException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IDEMPOTENCY_CONFLICT")
                ));
    }

    @Test
    void concurrentDispatchClaimsUseOneDurableChildStateTransition() throws Exception {
        UUID scanId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        ActiveScan scan = ActiveScan.readyToDispatch(
                scanId, actorId, accountId, "scan", "claim-key", "claim-fingerprint",
                new ActiveScanScopeSnapshot(List.of(marketId), List.of(marketId), List.of(), List.of(marketId), NOW), NOW
        );
        executions.save(requestedExecution(executionId, marketId));
        UUID scanMarketId = UUID.randomUUID();
        scanRepository.save(scan);
        scanRepository.saveMarkets(List.of(ActiveScanMarket.registered(
                scanMarketId, scanId, 0, marketId, executionId, NOW
        )));

        List<ActiveScanDispatchClaimService.ClaimResult> results = concurrently(
                () -> claims.claimForDispatch(scanId, scanMarketId),
                () -> claims.claimForDispatch(scanId, scanMarketId)
        );

        assertThat(results).allSatisfy(result -> assertThat(result.analysisExecutionId()).isEqualTo(executionId));
        assertThat(scanRepository.findMarketById(scanMarketId)).get()
                .extracting(ActiveScanMarket::status)
                .isEqualTo(ActiveScanMarketStatus.DISPATCH_REQUESTED);
        assertThat(executions.findById(executionId)).get()
                .extracting(AnalysisExecution::status)
                .isEqualTo(AnalysisExecutionStatus.ACCEPTED);
    }

    private ActiveScanScopeResolutionResult resolved(UUID accountId, UUID marketId) {
        return new ActiveScanScopeResolutionResult(
                accountId, "scan", List.of(marketId), List.of(marketId),
                List.of(new MarketEligibilityDecision(marketId, "ACH/EUR", "KRAKEN", true, List.of())),
                new EffectiveScanScope(List.of(marketId)), NOW
        );
    }

    private ActiveScanScopeResolutionResult emptyResolved(UUID accountId, UUID marketId) {
        return new ActiveScanScopeResolutionResult(
                accountId, "scan", List.of(marketId), List.of(marketId),
                List.of(new MarketEligibilityDecision(
                        marketId, "ACH/EUR", "KRAKEN", false,
                        List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE)
                )),
                new EffectiveScanScope(List.of()), NOW
        );
    }

    private AnalysisExecution requestedExecution(UUID executionId, UUID marketId) {
        return AnalysisExecution.requested(
                executionId,
                new IdempotencyKey("integration-execution-" + executionId),
                new AnalysisExecutionPolicy(
                        java.time.Duration.ofMinutes(5), java.time.Duration.ofSeconds(30), 0, 1,
                        new ContextLimits(10, 10, 10_000, 5, com.hope.trading.market_intelligence.domain.context.ContextClassification.PUBLIC),
                        new RetryPolicy(0, java.time.Duration.ZERO, java.util.Set.of()),
                        java.util.Map.of(), new DegradationPolicy(true, true, true, true)
                ),
                NOW, List.of(),
                new AnalysisExecutionProvenance(marketId, AnalysisExecutionMode.ACTIVE, "scan", "v1"),
                new AnalysisTraceMetadata(List.of())
        );
    }

    private <T> List<T> concurrently(ThrowingSupplier<T> first, ThrowingSupplier<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<T> firstResult = executor.submit(() -> {
                start.await();
                return first.get();
            });
            Future<T> secondResult = executor.submit(() -> {
                start.await();
                return second.get();
            });
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Outcome outcome(ThrowingSupplier<ActiveScan> supplier) {
        try {
            return new Outcome(true, supplier.get(), null);
        } catch (Exception exception) {
            return new Outcome(false, null, exception);
        }
    }

    private record Outcome(boolean success, ActiveScan scan, Exception error) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
