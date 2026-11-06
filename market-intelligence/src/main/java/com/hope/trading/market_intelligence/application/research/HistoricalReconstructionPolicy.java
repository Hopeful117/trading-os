package com.hope.trading.market_intelligence.application.research;

import java.util.Set;

/** Immutable capability boundary for the first historical reconstruction proof. */
public final class HistoricalReconstructionPolicy {
    public static final String OBJECTIVE = "historical-reconstruction-v1";
    public static final Set<String> ALLOWED_CAPABILITIES = Set.of(
            "trend-context-analysis",
            "market-structure-analysis",
            "ohlc-range-analysis",
            "ohlc-momentum-analysis");

    private HistoricalReconstructionPolicy() { }

    public static void requireAllowed(Set<String> capabilityIds) {
        if (capabilityIds == null || !ALLOWED_CAPABILITIES.equals(Set.copyOf(capabilityIds))) {
            throw new IllegalArgumentException(
                    "Historical reconstruction capability closure is not exact: " + capabilityIds);
        }
    }
}
