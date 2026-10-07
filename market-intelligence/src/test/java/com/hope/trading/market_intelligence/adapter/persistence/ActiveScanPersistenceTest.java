package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.execution.*;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.domain.scan.*;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ActiveScanPersistenceTest {
    @Autowired ActiveScanRepository scans;
    @Autowired JpaAnalysisExecutionRepository executions;

    @Test
    void persistsAndReloadsScanAndMarketSnapshot() {
        Instant now = Instant.parse("2026-08-20T12:00:00Z");
        UUID scanId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID eligible = UUID.randomUUID();
        UUID excluded = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        executions.save(AnalysisExecution.requested(
                executionId,
                new IdempotencyKey("active-scan-test"),
                new AnalysisExecutionPolicy(
                        java.time.Duration.ofMinutes(5),
                        java.time.Duration.ofSeconds(30),
                        0,
                        1,
                        new ContextLimits(10, 10, 10_000, 5, ContextClassification.PUBLIC),
                        new RetryPolicy(0, java.time.Duration.ZERO, java.util.Set.of()),
                        java.util.Map.of(),
                        new DegradationPolicy(true, true, true, true)
                ),
                now,
                List.of(),
                new AnalysisExecutionProvenance(eligible, AnalysisExecutionMode.ACTIVE, "scan", "v1"),
                new AnalysisTraceMetadata(List.of())
        ));
        ActiveScan scan = ActiveScan.readyToDispatch(
                scanId,
                actorId,
                accountId,
                "scan",
                "scan-key",
                "fingerprint",
                new ActiveScanScopeSnapshot(
                        List.of(eligible, excluded),
                        List.of(eligible, excluded),
                        List.of(
                                new ActiveScanDecisionSnapshot(eligible, "ACH/EUR", "KRAKEN", true, List.of()),
                                new ActiveScanDecisionSnapshot(excluded, "AI3/EUR", "KRAKEN", false,
                                        List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE))
                        ),
                        List.of(eligible),
                        now
                ),
                now
        );
        ActiveScanMarket excludedMarket = ActiveScanMarket.excluded(
                UUID.randomUUID(), scanId, 1, excluded,
                List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE), now
        );
        ActiveScanMarket registeredMarket = ActiveScanMarket.registered(
                UUID.randomUUID(), scanId, 0, eligible, executionId, now
        );

        scans.save(scan);
        scans.saveMarkets(List.of(registeredMarket, excludedMarket));

        assertThat(scans.findByActorIdAndIdempotencyKey(actorId, "scan-key")).get()
                .extracting(ActiveScan::requestFingerprint)
                .isEqualTo("fingerprint");
        assertThat(scans.findMarketsByScanId(scanId))
                .hasSize(2)
                .extracting(ActiveScanMarket::ordinal)
                .containsExactly(0, 1);
    }

    @Test
    void persistsAndReloadsBoundedFailureCode() {
        Instant now = Instant.parse("2026-08-20T12:00:00Z");
        UUID executionId = UUID.randomUUID();
        AnalysisExecution execution = AnalysisExecution.requested(
                executionId,
                new IdempotencyKey("failure-code-" + executionId),
                new AnalysisExecutionPolicy(
                        java.time.Duration.ofMinutes(5), java.time.Duration.ofSeconds(30), 0, 1,
                        new ContextLimits(10, 10, 10_000, 5, ContextClassification.PUBLIC),
                        new RetryPolicy(0, java.time.Duration.ZERO, java.util.Set.of()),
                        java.util.Map.of(), new DegradationPolicy(true, true, true, true)
                ),
                now,
                List.of(),
                new AnalysisExecutionProvenance(UUID.randomUUID(), AnalysisExecutionMode.ACTIVE, "scan", "v1"),
                new AnalysisTraceMetadata(List.of())
        ).transitionTo(AnalysisExecutionStatus.ACCEPTED, now.plusSeconds(1))
                .fail("MARKET_SNAPSHOT_UNAVAILABLE", now.plusSeconds(2));

        executions.save(execution);

        assertThat(executions.findById(executionId)).get()
                .extracting(AnalysisExecution::failureCode)
                .isEqualTo(Optional.of("MARKET_SNAPSHOT_UNAVAILABLE"));
    }

    @Test
    void concurrentMarketDispatchClaimsAllowOnlyOneDurableWinner() throws Exception {
        Instant now = Instant.parse("2026-08-20T12:00:00Z");
        UUID scanId = UUID.randomUUID();
        UUID scanMarketId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        executions.save(AnalysisExecution.requested(
                executionId,
                new IdempotencyKey("concurrent-execution"),
                new AnalysisExecutionPolicy(
                        java.time.Duration.ofMinutes(5),
                        java.time.Duration.ofSeconds(30),
                        0,
                        1,
                        new ContextLimits(10, 10, 10_000, 5, ContextClassification.PUBLIC),
                        new RetryPolicy(0, java.time.Duration.ZERO, java.util.Set.of()),
                        java.util.Map.of(),
                        new DegradationPolicy(true, true, true, true)
                ),
                now,
                List.of(),
                new AnalysisExecutionProvenance(marketId, AnalysisExecutionMode.ACTIVE, "scan", "v1"),
                new AnalysisTraceMetadata(List.of())
        ));
        ActiveScan scan = ActiveScan.readyToDispatch(
                scanId, UUID.randomUUID(), UUID.randomUUID(), "scan", "concurrent-key", "fingerprint",
                new ActiveScanScopeSnapshot(List.of(marketId), List.of(marketId), List.of(), List.of(marketId), now), now
        );
        scans.save(scan);
        scans.saveMarkets(List.of(ActiveScanMarket.registered(
                scanMarketId, scanId, 0, marketId, executionId, now
        )));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return scans.transitionMarketStatus(
                        scanMarketId, ActiveScanMarketStatus.REGISTERED,
                        ActiveScanMarketStatus.DISPATCH_REQUESTED, now.plusSeconds(1)
                );
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return scans.transitionMarketStatus(
                        scanMarketId, ActiveScanMarketStatus.REGISTERED,
                        ActiveScanMarketStatus.DISPATCH_REQUESTED, now.plusSeconds(1)
                );
            });
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
            assertThat(scans.findMarketById(scanMarketId)).get()
                    .extracting(ActiveScanMarket::status)
                    .isEqualTo(ActiveScanMarketStatus.DISPATCH_REQUESTED);
        } finally {
            executor.shutdownNow();
        }
    }
}
