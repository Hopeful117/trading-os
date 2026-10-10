package com.hope.trading.market_intelligence.domain.opportunity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Construction primitive restricted in production by the OpportunityBuilder boundary. */
public final class OpportunityFactory {
    public TradingOpportunity create(Values values) {
        return new TradingOpportunity(
                values.id(), values.version(), values.status(), values.instrument(), values.direction(),
                values.scenario(), values.timeframe(), values.type(), values.origin(), values.score(),
                values.explanation(), values.observations(), values.aiAnalyses(), values.evaluatedAt(),
                values.validFrom(), values.validUntil(), values.createdAt(), values.strategyMatchId(),
                values.setupSnapshot(), values.marketId(), values.accountId(), values.sourceScanId(),
                values.sourceScanMarketId(), values.analysisExecutionId());
    }

    public record Values(OpportunityId id, OpportunityVersion version, OpportunityStatus status,
            String instrument, OpportunityDirection direction, String scenario, String timeframe,
            OpportunityType type, OpportunityOrigin origin, OpportunityScore score, String explanation,
            Set<ObservationReference> observations, Set<AiAnalysisReference> aiAnalyses,
            Instant evaluatedAt, Instant validFrom, Instant validUntil, Instant createdAt,
            UUID strategyMatchId, OpportunitySetupSnapshot setupSnapshot,
             UUID marketId, UUID accountId, UUID sourceScanId, UUID sourceScanMarketId,
             UUID analysisExecutionId) {
        public Values(
                OpportunityId id, OpportunityVersion version, OpportunityStatus status,
                String instrument, OpportunityDirection direction, String scenario,
                String timeframe, OpportunityType type, OpportunityOrigin origin,
                OpportunityScore score, String explanation,
                Set<ObservationReference> observations, Set<AiAnalysisReference> aiAnalyses,
                Instant evaluatedAt, Instant validFrom, Instant validUntil, Instant createdAt,
                UUID strategyMatchId, OpportunitySetupSnapshot setupSnapshot, UUID marketId) {
            this(id, version, status, instrument, direction, scenario, timeframe, type, origin,
                    score, explanation, observations, aiAnalyses, evaluatedAt, validFrom,
                    validUntil, createdAt, strategyMatchId, setupSnapshot, marketId,
                    null, null, null, null);
        }
    }
}
