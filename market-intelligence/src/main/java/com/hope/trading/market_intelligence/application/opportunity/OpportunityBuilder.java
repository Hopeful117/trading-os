package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.time.Instant;

/** Internal constructor. OpportunityEngine is its only production caller. */
final class OpportunityBuilder {
    private final OpportunityFactory factory;

    OpportunityBuilder(OpportunityFactory factory) {
        this.factory = factory;
    }

    TradingOpportunity create(
            OpportunityId id, CreateOpportunityCommand command,
            OpportunityFusionResult result, Instant createdAt
    ) {
        return factory.create(
                id, new OpportunityVersion(1), OpportunityStatus.DETECTED,
                command.instrument(), command.direction(), command.scenario(),
                command.timeframe(), result.type(), command.origin(), result.score(),
                result.explanation(), result.observations(), result.aiAnalyses(),
                command.evaluatedAt(), result.validFrom(), result.validUntil(), createdAt,
                command.strategyMatchId());
    }

    TradingOpportunity nextVersion(
            TradingOpportunity previous, CreateOpportunityCommand command,
            OpportunityFusionResult result, Instant createdAt
    ) {
        return factory.create(
                previous.id(), previous.version().next(), OpportunityStatus.DETECTED,
                command.instrument(), command.direction(), command.scenario(),
                command.timeframe(), result.type(), command.origin(), result.score(),
                result.explanation(), result.observations(), result.aiAnalyses(),
                command.evaluatedAt(), result.validFrom(), result.validUntil(), createdAt,
                previous.strategyMatchId().orElse(command.strategyMatchId()));
    }

    TradingOpportunity transition(
            TradingOpportunity previous, OpportunityStatus target, Instant createdAt
    ) {
        return factory.create(
                previous.id(), previous.version().next(), target, previous.instrument(),
                previous.direction(), previous.scenario(), previous.timeframe(), previous.type(),
                previous.origin(), previous.score(), previous.explanation(),
                previous.observations(), previous.aiAnalyses(), previous.evaluatedAt(),
                previous.validFrom(), previous.validUntil().orElse(null), createdAt,
                previous.strategyMatchId().orElse(null));
    }
}
