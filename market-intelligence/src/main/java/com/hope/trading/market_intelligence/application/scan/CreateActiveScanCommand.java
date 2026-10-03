package com.hope.trading.market_intelligence.application.scan;

import java.util.List;
import java.util.UUID;
import com.hope.trading.market_intelligence.domain.scope.MarketScopeMode;

public record CreateActiveScanCommand(
        UUID actorId,
        String idempotencyKey,
        UUID accountId,
        String objective,
        List<UUID> requestedMarketIds,
        MarketScopeMode scopeMode
) {
    public CreateActiveScanCommand {
        if (scopeMode == null && requestedMarketIds != null
                && requestedMarketIds.stream().anyMatch(java.util.Objects::nonNull)) {
            scopeMode = MarketScopeMode.SELECTED;
        }
    }

    public CreateActiveScanCommand(UUID actorId, String idempotencyKey, UUID accountId,
                                   String objective, List<UUID> requestedMarketIds) {
        this(actorId, idempotencyKey, accountId, objective, requestedMarketIds,
                requestedMarketIds != null && requestedMarketIds.stream().anyMatch(java.util.Objects::nonNull)
                        ? MarketScopeMode.SELECTED : null);
    }
}
