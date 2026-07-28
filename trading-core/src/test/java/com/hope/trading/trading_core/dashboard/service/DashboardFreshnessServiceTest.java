package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.dashboard.model.DashboardDataStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardFreshnessServiceTest {
    private final DashboardFreshnessService service = new DashboardFreshnessService();

    @Test
    void detectsStaleBrokerData() {
        var freshness = service.evaluate(
                Instant.now().minusSeconds(60), List.of(Instant.now()),
                true, true, true, List.of()
        );

        assertThat(freshness.status()).isEqualTo(DashboardDataStatus.STALE);
        assertThat(freshness.brokerDataStale()).isTrue();
    }

    @Test
    void detectsStaleMarketData() {
        var freshness = service.evaluate(
                Instant.now(), List.of(Instant.now().minusSeconds(60)),
                true, true, true, List.of()
        );

        assertThat(freshness.status()).isEqualTo(DashboardDataStatus.STALE);
        assertThat(freshness.marketDataStale()).isTrue();
    }

    @Test
    void marksPartialMarketFailureAsDegraded() {
        var freshness = service.evaluate(
                Instant.now(), List.of(), true, false, true, List.of()
        );

        assertThat(freshness.status()).isEqualTo(DashboardDataStatus.DEGRADED);
    }

    @Test
    void marksBrokerFailureAsUnavailable() {
        var freshness = service.evaluate(
                null, List.of(), false, false, false, List.of()
        );

        assertThat(freshness.status()).isEqualTo(DashboardDataStatus.UNAVAILABLE);
    }
}
