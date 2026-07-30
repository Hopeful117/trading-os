package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.util.stream.Collectors;

final class TradingOpportunityMapper {
    private final OpportunityFactory factory = new OpportunityFactory();

    TradingOpportunityEntity toEntity(TradingOpportunity value) {
        return new TradingOpportunityEntity(
                value.id().value(), value.version().value(), value.status().name(),
                value.instrument(), value.direction().name(), value.scenario(),
                value.timeframe(), value.type().name(), value.origin().name(),
                value.score().value(), value.explanation(),
                value.observations().stream().map(ObservationReference::observationId)
                        .collect(Collectors.toUnmodifiableSet()),
                value.aiAnalyses().stream().map(AiAnalysisReference::analysisId)
                        .collect(Collectors.toUnmodifiableSet()),
                value.evaluatedAt(), value.validFrom(), value.validUntil().orElse(null),
                value.createdAt());
    }

    TradingOpportunity toDomain(TradingOpportunityEntity value) {
        return factory.create(
                new OpportunityId(value.id()), new OpportunityVersion(value.version()),
                OpportunityStatus.valueOf(value.status()), value.instrument(),
                OpportunityDirection.valueOf(value.direction()), value.scenario(),
                value.timeframe(), OpportunityType.valueOf(value.type()),
                OpportunityOrigin.valueOf(value.origin()), new OpportunityScore(value.score()),
                value.explanation(),
                value.observationIds().stream().map(ObservationReference::new)
                        .collect(Collectors.toUnmodifiableSet()),
                value.aiAnalysisIds().stream().map(AiAnalysisReference::new)
                        .collect(Collectors.toUnmodifiableSet()),
                value.evaluatedAt(), value.validFrom(), value.validUntil(), value.createdAt());
    }
}
