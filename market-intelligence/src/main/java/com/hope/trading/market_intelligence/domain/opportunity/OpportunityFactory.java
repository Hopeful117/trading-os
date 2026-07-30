package com.hope.trading.market_intelligence.domain.opportunity;

import java.time.Instant;
import java.util.Set;

/** Construction primitive restricted in production by the OpportunityBuilder boundary. */
public final class OpportunityFactory {
    public TradingOpportunity create(
            OpportunityId id, OpportunityVersion version, OpportunityStatus status,
            String instrument, OpportunityDirection direction, String scenario,
            String timeframe, OpportunityType type, OpportunityOrigin origin,
            OpportunityScore score, String explanation,
            Set<ObservationReference> observations, Set<AiAnalysisReference> aiAnalyses,
            Instant evaluatedAt, Instant validFrom, Instant validUntil, Instant createdAt
    ) {
        return new TradingOpportunity(
                id, version, status, instrument, direction, scenario, timeframe, type,
                origin, score, explanation, observations, aiAnalyses, evaluatedAt,
                validFrom, validUntil, createdAt);
    }
}
