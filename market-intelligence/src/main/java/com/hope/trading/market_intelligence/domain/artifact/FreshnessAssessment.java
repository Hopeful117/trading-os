package com.hope.trading.market_intelligence.domain.artifact;

import java.time.Instant;

public record FreshnessAssessment(
        FreshnessStatus status,
        boolean reusable,
        boolean warning,
        String reason,
        Instant assessedAt
) {
}
