package com.hope.trading.market_intelligence.adapter.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;
import com.hope.trading.market_intelligence.domain.scope.MarketScopeMode;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;

public record CreateActiveScanRequestDto(
        @NotNull UUID accountId,
        UUID brokerAccountId,
        @Size(max = 500) String objective,
        List<UUID> requestedMarketIds,
        MarketScopeMode scopeMode,
        TradeabilityEvidenceProfile tradeabilityEvidenceProfile,
        UUID sourcePassiveOpportunityId,
        Long sourcePassiveOpportunityVersion
) {
    public CreateActiveScanRequestDto(UUID accountId, UUID brokerAccountId, String objective,
                                      List<UUID> requestedMarketIds, MarketScopeMode scopeMode,
                                      TradeabilityEvidenceProfile tradeabilityEvidenceProfile) {
        this(accountId, brokerAccountId, objective, requestedMarketIds, scopeMode,
                tradeabilityEvidenceProfile, null, null);
    }

    public CreateActiveScanRequestDto(UUID accountId, String objective, List<UUID> requestedMarketIds) {
        this(accountId, null, objective, requestedMarketIds, null, null);
    }

    public CreateActiveScanRequestDto(UUID accountId, String objective,
                                      List<UUID> requestedMarketIds, MarketScopeMode scopeMode) {
        this(accountId, null, objective, requestedMarketIds, scopeMode, null);
    }
}
