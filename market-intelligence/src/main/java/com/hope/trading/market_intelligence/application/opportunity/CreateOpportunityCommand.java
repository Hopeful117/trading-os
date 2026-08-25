package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.time.Instant;
import java.util.*;

import java.util.UUID;

public record CreateOpportunityCommand(
        String instrument,
        OpportunityDirection direction,
        String scenario,
        String timeframe,
        OpportunityOrigin origin,
        Set<ObservationReference> observations,
        Set<AiAnalysisReference> aiAnalyses,
        Instant evaluatedAt,
        Instant validUntil,
        UUID strategyMatchId,
        UUID opportunityId,
        OpportunitySetupSnapshot setupSnapshot
) {
    public CreateOpportunityCommand {
        instrument = required(instrument, "instrument");
        Objects.requireNonNull(direction, "direction");
        scenario = required(scenario, "scenario");
        timeframe = required(timeframe, "timeframe");
        Objects.requireNonNull(origin, "origin");
        observations = Set.copyOf(observations);
        if (observations.isEmpty()) {
            throw new IllegalArgumentException("At least one Observation is required");
        }
        aiAnalyses = aiAnalyses == null ? Set.of() : Set.copyOf(aiAnalyses);
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        if (validUntil != null && !validUntil.isAfter(evaluatedAt)) {
            throw new IllegalArgumentException("validUntil must follow evaluation");
        }
        Objects.requireNonNull(strategyMatchId,
                "strategyMatchId is required (ADR-034: opportunities derive from a match)");
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return result;
    }
}
