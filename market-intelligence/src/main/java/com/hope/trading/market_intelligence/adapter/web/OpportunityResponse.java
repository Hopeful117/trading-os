package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record OpportunityResponse(
        UUID id, long version, String status, String instrument, String direction,
        String scenario, String timeframe, String type, String origin, BigDecimal score,
        String explanation, Set<UUID> observationIds, Set<UUID> aiAnalysisIds,
        Instant evaluatedAt, Instant validFrom, Instant validUntil, Instant createdAt,
        UUID strategyMatchId
) {
    static OpportunityResponse from(TradingOpportunity value) {
        return new OpportunityResponse(
                value.id().value(), value.version().value(), value.status().name(),
                value.instrument(), value.direction().name(), value.scenario(),
                value.timeframe(), value.type().name(), value.origin().name(),
                value.score().value(), value.explanation(),
                value.observations().stream().map(ObservationReference::observationId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                value.aiAnalyses().stream().map(AiAnalysisReference::analysisId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                value.evaluatedAt(), value.validFrom(), value.validUntil().orElse(null),
                value.createdAt(), value.strategyMatchId().orElse(null));
    }
}
