package com.hope.trading.market_intelligence.domain.scope;

import java.util.List;
import java.util.UUID;

public record ActiveScanScopeResolutionRequest(
        UUID accountId,
        UUID brokerAccountId,
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
        this(accountId, null, objective, requestedMarketIds,
                requestedMarketIds != null && requestedMarketIds.stream().anyMatch(java.util.Objects::nonNull)
                        ? MarketScopeMode.SELECTED : null);
    }

    public ActiveScanScopeResolutionRequest(UUID accountId, String objective,
                                            List<UUID> requestedMarketIds, MarketScopeMode scopeMode) {
        this(accountId, null, objective, requestedMarketIds, scopeMode);
    }
}
