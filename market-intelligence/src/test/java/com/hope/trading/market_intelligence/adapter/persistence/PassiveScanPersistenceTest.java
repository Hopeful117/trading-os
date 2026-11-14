package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.PassiveScanRunRepository;
import com.hope.trading.market_intelligence.application.port.PassiveDispatchCapacityRepository;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import com.hope.trading.market_intelligence.domain.execution.*;
import com.hope.trading.market_intelligence.domain.passive.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PassiveScanPersistenceTest {
    @Autowired PassiveScanRunRepository scans;
    @Autowired JpaAnalysisExecutionRepository executions;
    @Autowired PassiveDispatchCapacityRepository capacity;

    @Test
    void persistsAndReloadsImmutableRunAndMarketSnapshot() {
        Instant now = Instant.parse("2026-11-13T00:00:00Z");
        UUID runId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        executions.save(execution(executionId, marketId, "passive-execution-" + executionId, now));

        PassiveScanRun run = PassiveScanRun.requested(runId, "passive-run-" + runId, "fingerprint",
                "SYSTEM", now, now.plusSeconds(300), "v1", "sha256-v1", "universe", now, List.of("INFO:x"), now);
        PassiveScanMarket market = PassiveScanMarket.registered(UUID.randomUUID(), runId, 0, marketId,
                "CTRADER", "CFD", Duration.ofMinutes(5), TradeabilityEvidenceProfile.FOREX_QUOTE_BASIC,
                executionId, now);

        scans.saveRun(run);
        scans.saveMarkets(List.of(market));
        scans.saveAttempt(UUID.randomUUID(), market.scanMarketId(), 1, executionId,
                "passive-attempt-" + executionId, now);

        assertThat(scans.findById(runId)).get().extracting(PassiveScanRun::universeFingerprint)
                .isEqualTo("universe");
        assertThat(scans.findMarketsByRunId(runId)).singleElement()
                .extracting(PassiveScanMarket::provider, PassiveScanMarket::productType,
                        PassiveScanMarket::minimumEvidenceProfile)
                .containsExactly("CTRADER", "CFD", TradeabilityEvidenceProfile.FOREX_QUOTE_BASIC);
    }

    @Test
    void conditionalClaimAllowsOnlyOneWinner() {
        Instant now = Instant.parse("2026-11-13T00:00:00Z");
        UUID runId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        executions.save(execution(executionId, marketId, "claim-execution-" + executionId, now));
        PassiveScanRun run = PassiveScanRun.requested(runId, "claim-run-" + runId, "fingerprint",
                "SYSTEM", now, now.plusSeconds(300), "v1", "sha256-v1", "universe", now, List.of(), now);
        PassiveScanMarket market = PassiveScanMarket.registered(UUID.randomUUID(), runId, 0, marketId,
                "CTRADER", "CFD", Duration.ofMinutes(5), TradeabilityEvidenceProfile.UNSUPPORTED,
                executionId, now);
        scans.saveRun(run);
        scans.saveMarkets(List.of(market));

        boolean first = scans.claimMarket(market.scanMarketId(), PassiveScanMarketStatus.REGISTERED,
                market.version(), UUID.randomUUID(), now.plusSeconds(30), now.plusSeconds(1));
        boolean second = scans.claimMarket(market.scanMarketId(), PassiveScanMarketStatus.REGISTERED,
                market.version(), UUID.randomUUID(), now.plusSeconds(30), now.plusSeconds(1));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(scans.findMarketById(market.scanMarketId())).get()
                .extracting(PassiveScanMarket::status)
                .isEqualTo(PassiveScanMarketStatus.DISPATCH_REQUESTED);
    }

    @Test
    void durableCapacityAllowsOnlyConfiguredConcurrentLeases() {
        Instant now = Instant.parse("2026-11-13T00:00:00Z");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        executions.save(execution(executionId, marketId, "capacity-execution-" + executionId, now));
        scans.saveRun(PassiveScanRun.requested(runId, "capacity-run-" + runId, "fingerprint",
                "SYSTEM", now, now.plusSeconds(300), "v1", "sha256-v1", "universe", now, List.of(), now));
        UUID scanMarketId = UUID.randomUUID();
        scans.saveMarkets(List.of(PassiveScanMarket.registered(scanMarketId, runId, 0, marketId,
                "CTRADER", "CFD", Duration.ofMinutes(5), TradeabilityEvidenceProfile.UNSUPPORTED,
                executionId, now)));
        assertThat(capacity.acquire(first, scanMarketId, 1, now.plusSeconds(30), now)).isTrue();
        assertThat(capacity.acquire(second, scanMarketId, 1, now.plusSeconds(30), now)).isFalse();
        assertThat(capacity.release(first, now.plusSeconds(1))).isTrue();
        assertThat(capacity.acquire(second, scanMarketId, 1, now.plusSeconds(30), now.plusSeconds(1))).isTrue();
        assertThat(capacity.release(second, now.plusSeconds(2))).isTrue();
    }

    private AnalysisExecution execution(UUID id, UUID marketId, String key, Instant now) {
        return AnalysisExecution.requested(id, new IdempotencyKey(key), new AnalysisExecutionPolicy(
                Duration.ofMinutes(5), Duration.ofSeconds(30), 0, 1,
                new ContextLimits(10, 10, 10_000, 5, ContextClassification.PUBLIC),
                new RetryPolicy(0, Duration.ZERO, java.util.Set.of()), java.util.Map.of(),
                new DegradationPolicy(true, true, true, true)), now, List.of(),
                new AnalysisExecutionProvenance(marketId, AnalysisExecutionMode.PASSIVE, "PASSIVE_SCAN", "v1"),
                new AnalysisTraceMetadata(List.of()));
    }
}
