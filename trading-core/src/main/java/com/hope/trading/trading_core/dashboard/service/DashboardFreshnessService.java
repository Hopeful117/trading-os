package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.dashboard.model.DashboardDataStatus;
import com.hope.trading.trading_core.dashboard.model.DashboardFreshness;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Service
public class DashboardFreshnessService {
    static final Duration STALE_AFTER = Duration.ofSeconds(15);

    public DashboardFreshness evaluate(
            Instant brokerDataAt,
            Collection<Instant> marketTimestamps,
            boolean brokerAvailable,
            boolean marketAvailable,
            boolean marketDataRequired,
            List<String> warnings
    ) {
        Instant now = Instant.now();
        Instant marketDataAt = marketTimestamps.stream()
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        boolean brokerStale = brokerDataAt != null
                && brokerDataAt.isBefore(now.minus(STALE_AFTER));
        boolean marketStale = marketDataRequired && marketDataAt != null
                && marketDataAt.isBefore(now.minus(STALE_AFTER));

        List<String> allWarnings = new ArrayList<>(warnings);
        if (!brokerAvailable) {
            allWarnings.add("Broker Service indisponible");
        } else if (brokerStale) {
            allWarnings.add("Données broker obsolètes");
        }
        if (marketDataRequired && !marketAvailable) {
            allWarnings.add("Market Data Service indisponible");
        } else if (marketStale) {
            allWarnings.add("Données de marché obsolètes");
        }

        DashboardDataStatus status;
        if (!brokerAvailable) {
            status = DashboardDataStatus.UNAVAILABLE;
        } else if (brokerStale || marketStale) {
            status = DashboardDataStatus.STALE;
        } else if ((marketDataRequired && !marketAvailable) || !allWarnings.isEmpty()) {
            status = DashboardDataStatus.DEGRADED;
        } else {
            status = DashboardDataStatus.LIVE;
        }

        return new DashboardFreshness(
                status, brokerDataAt, marketDataAt, now,
                brokerStale, marketStale, allWarnings
        );
    }
}
