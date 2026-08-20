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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
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
}
