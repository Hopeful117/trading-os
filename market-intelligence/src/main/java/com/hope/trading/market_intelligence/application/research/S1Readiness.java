package com.hope.trading.market_intelligence.application.research;

import java.util.List;
import java.util.Set;

public record S1Readiness(
        long totalSignals,
        long longSignals,
        long shortSignals,
        Set<String> aggregateRegimes,
        long replayChecks,
        long replayAvailable,
        double replayCoverage,
        long feeChecks,
        long feeAvailable,
        double feeCoverage,
        boolean ready,
        List<String> blockingReasons
) {
    public S1Readiness {
        aggregateRegimes = Set.copyOf(aggregateRegimes);
        blockingReasons = List.copyOf(blockingReasons);
    }
}
