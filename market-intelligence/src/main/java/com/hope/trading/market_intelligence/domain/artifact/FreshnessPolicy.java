package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;

import java.time.Duration;
import java.util.Set;

public record FreshnessPolicy(
        Duration maximumAge,
        Duration staleTolerance,
        Set<AnalysisExecutionMode> staleAllowedModes,
        boolean allowUnknown,
        boolean critical
) {
    public FreshnessPolicy {
        if (maximumAge == null || maximumAge.isNegative()
                || staleTolerance == null || staleTolerance.isNegative()) {
            throw new IllegalArgumentException("Freshness durations cannot be negative");
        }
        staleAllowedModes = Set.copyOf(staleAllowedModes);
    }

    public static FreshnessPolicy strict(Duration maximumAge) {
        return new FreshnessPolicy(
                maximumAge, Duration.ZERO, Set.of(), false, true
        );
    }

    public boolean allowsStale(AnalysisExecutionMode mode) {
        return !critical && staleAllowedModes.contains(mode);
    }
}
