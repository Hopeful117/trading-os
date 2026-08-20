package com.hope.trading.market_intelligence.domain.scope;

import java.util.List;
import java.util.UUID;

public record ActiveScanScopeResolutionRequest(
        UUID accountId,
        String objective,
        List<UUID> requestedMarketIds
) {
}
