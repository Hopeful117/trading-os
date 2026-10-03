package com.hope.trading.market_intelligence.adapter.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;
import com.hope.trading.market_intelligence.domain.scope.MarketScopeMode;

public record ActiveScanScopeResolutionRequestDto(
        @NotNull UUID accountId,
        @Size(max = 500) String objective,
        List<UUID> requestedMarketIds,
        MarketScopeMode scopeMode
) {
    public ActiveScanScopeResolutionRequestDto(UUID accountId, String objective, List<UUID> requestedMarketIds) {
        this(accountId, objective, requestedMarketIds, null);
    }
}
