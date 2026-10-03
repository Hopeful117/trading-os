package com.hope.trading.market_intelligence.domain.scope;

import java.util.List;
import java.util.UUID;

public record ActiveScanScopeResolutionRequest(
        UUID accountId,
        String objective,
        List<UUID> requestedMarketIds,
        MarketScopeMode scopeMode
) {
    public ActiveScanScopeResolutionRequest {
        if (scopeMode == null && requestedMarketIds != null
                && requestedMarketIds.stream().anyMatch(java.util.Objects::nonNull)) {
            scopeMode = MarketScopeMode.SELECTED;
        }
    }

    public ActiveScanScopeResolutionRequest(UUID accountId, String objective, List<UUID> requestedMarketIds) {
        this(accountId, objective, requestedMarketIds,
                requestedMarketIds != null && requestedMarketIds.stream().anyMatch(java.util.Objects::nonNull)
                        ? MarketScopeMode.SELECTED : null);
    }
}
