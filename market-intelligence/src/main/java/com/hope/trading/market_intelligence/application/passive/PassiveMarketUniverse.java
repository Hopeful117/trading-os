package com.hope.trading.market_intelligence.application.passive;

import java.time.Instant;
import java.util.List;

public record PassiveMarketUniverse(
        String configurationVersion,
        String fingerprintAlgorithm,
        String fingerprint,
        Instant resolvedAt,
        List<PassiveMarketEntry> entries,
        List<PassiveMarketUniverseDiagnostic> diagnostics
) {
    public PassiveMarketUniverse {
        entries = List.copyOf(entries);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean enabled() {
        return !entries.isEmpty();
    }
}
