package com.hope.trading.market_intelligence.domain.scope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveScanScopeResolutionResult(
        UUID accountId,
        String objective,
        List<UUID> requestedMarketIds,
        List<UUID> candidateMarketIds,
        List<MarketEligibilityDecision> decisions,
        EffectiveScanScope effectiveScope,
        Instant resolvedAt
) {
}
