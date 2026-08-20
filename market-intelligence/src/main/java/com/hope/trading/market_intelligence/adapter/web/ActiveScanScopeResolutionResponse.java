package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.scope.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveScanScopeResolutionResponse(
        UUID accountId,
        String objective,
        List<UUID> requestedMarketIds,
        List<UUID> candidateMarketIds,
        List<MarketEligibilityDecisionResponse> decisions,
        List<UUID> effectiveMarketIds,
        Instant resolvedAt
) {
    static ActiveScanScopeResolutionResponse from(ActiveScanScopeResolutionResult result) {
        return new ActiveScanScopeResolutionResponse(
                result.accountId(),
                result.objective(),
                result.requestedMarketIds(),
                result.candidateMarketIds(),
                result.decisions().stream().map(MarketEligibilityDecisionResponse::from).toList(),
                result.effectiveScope().marketIds(),
                result.resolvedAt()
        );
    }

    public record MarketEligibilityDecisionResponse(
            UUID marketId,
            String symbol,
            String provider,
            boolean eligible,
            List<MarketEligibilityReason> reasons
    ) {
        static MarketEligibilityDecisionResponse from(MarketEligibilityDecision value) {
            return new MarketEligibilityDecisionResponse(
                    value.marketId(),
                    value.symbol(),
                    value.provider(),
                    value.eligible(),
                    value.reasons()
            );
        }
    }
}
