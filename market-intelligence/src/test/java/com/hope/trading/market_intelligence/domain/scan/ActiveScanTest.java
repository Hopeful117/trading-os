package com.hope.trading.market_intelligence.domain.scan;

import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActiveScanTest {
    private final Instant now = Instant.parse("2026-08-20T12:00:00Z");

    @Test
    void readyScanCanTransitionToDispatchRequested() {
        ActiveScan scan = ActiveScan.readyToDispatch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                " objective ",
                "key-1",
                "fingerprint-1",
                snapshot(List.of(UUID.randomUUID())),
                now
        );

        ActiveScan updated = scan.markDispatchRequested(now.plusSeconds(5));

        assertThat(updated.status()).isEqualTo(ActiveScanStatus.DISPATCH_REQUESTED);
        assertThat(updated.updatedAt()).isEqualTo(now.plusSeconds(5));
        assertThat(updated.objective()).isEqualTo("objective");
    }

    @Test
    void completedNoWorkCannotTransitionToDispatchRequested() {
        ActiveScan scan = ActiveScan.completedNoWork(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "scan",
                "key-1",
                "fingerprint-1",
                snapshot(List.of()),
                now
        );

        assertThatThrownBy(() -> scan.markDispatchRequested(now.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void dispatchRequestedCanAdvanceToRunning() {
        ActiveScan scan = ActiveScan.readyToDispatch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "scan",
                "key-1",
                "fingerprint-1",
                snapshot(List.of(UUID.randomUUID())),
                now
        ).markDispatchRequested(now.plusSeconds(1));

        ActiveScan updated = scan.reconcileTo(ActiveScanStatus.RUNNING, now.plusSeconds(2));

        assertThat(updated.status()).isEqualTo(ActiveScanStatus.RUNNING);
    }

    @Test
    void runningCanAdvanceToTerminalStatus() {
        ActiveScan scan = ActiveScan.readyToDispatch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "scan",
                "key-1",
                "fingerprint-1",
                snapshot(List.of(UUID.randomUUID())),
                now
        ).markDispatchRequested(now.plusSeconds(1))
                .reconcileTo(ActiveScanStatus.RUNNING, now.plusSeconds(2));

        ActiveScan updated = scan.reconcileTo(ActiveScanStatus.PARTIALLY_COMPLETED, now.plusSeconds(3));

        assertThat(updated.status()).isEqualTo(ActiveScanStatus.PARTIALLY_COMPLETED);
        assertThat(updated.status().isTerminal()).isTrue();
    }

    @Test
    void registeredMarketCanTransitionToDispatchRequested() {
        ActiveScanMarket market = ActiveScanMarket.registered(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                UUID.randomUUID(),
                UUID.randomUUID(),
                now
        );

        ActiveScanMarket updated = market.markDispatchRequested(now.plusSeconds(1));

        assertThat(updated.status()).isEqualTo(ActiveScanMarketStatus.DISPATCH_REQUESTED);
    }

    @Test
    void excludedMarketCannotCarryAnalysisExecution() {
        assertThatThrownBy(() -> ActiveScanMarket.restore(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                UUID.randomUUID(),
                false,
                List.of(MarketEligibilityReason.MARKET_NOT_FOUND),
                ActiveScanMarketStatus.EXCLUDED,
                UUID.randomUUID(),
                now,
                now
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private ActiveScanScopeSnapshot snapshot(List<UUID> effectiveMarkets) {
        return new ActiveScanScopeSnapshot(
                List.of(),
                effectiveMarkets,
                effectiveMarkets.stream().map(marketId -> new ActiveScanDecisionSnapshot(
                        marketId,
                        "BTC/USD",
                        "KRAKEN",
                        true,
                        List.of()
                )).toList(),
                effectiveMarkets,
                now
        );
    }
}
