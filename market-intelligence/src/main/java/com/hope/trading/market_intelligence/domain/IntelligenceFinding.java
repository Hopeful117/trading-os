package com.hope.trading.market_intelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record IntelligenceFinding(
        String findingId,
        String capabilityId,
        AnalysisOrigin origin,
        FindingType type,
        String title,
        String description,
        Map<String, BigDecimal> metrics,
        BigDecimal confidence,
        Set<ContextSectionType> contextSectionsUsed,
        Instant generatedAt
) {
    public IntelligenceFinding {
        metrics = Map.copyOf(metrics);
        contextSectionsUsed = Set.copyOf(contextSectionsUsed);
    }
}
