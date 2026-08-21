package com.hope.trading.market_intelligence.adapter.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

record TradingOpportunityEntity(
        UUID id, long version, String status, String instrument, String direction,
        String scenario, String timeframe, String type, String origin, BigDecimal score,
        String explanation, Set<UUID> observationIds, Set<UUID> aiAnalysisIds,
        Instant evaluatedAt, Instant validFrom, Instant validUntil, Instant createdAt,
        UUID strategyMatchId
) {
    TradingOpportunityEntity {
        observationIds = Set.copyOf(observationIds);
        aiAnalysisIds = Set.copyOf(aiAnalysisIds);
    }
}
