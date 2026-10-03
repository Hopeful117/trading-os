package com.hope.trading.market_intelligence.domain.scope;

import java.time.Instant;

public record MarketFactsProvenance(
        String activityStatus,
        String readinessStatus,
        Instant generatedAt,
        Instant activityObservationBoundary,
        Instant readinessObservationBoundary,
        String calculationVersion
) {
}
