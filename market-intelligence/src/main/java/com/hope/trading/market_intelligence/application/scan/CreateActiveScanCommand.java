package com.hope.trading.market_intelligence.application.scan;

import java.util.List;
import java.util.UUID;
import com.hope.trading.market_intelligence.domain.scope.MarketScopeMode;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;

public record CreateActiveScanCommand(
        UUID actorId,
        String idempotencyKey,
        UUID accountId,
        UUID brokerAccountId,
        String objective,
        List<UUID> requestedMarketIds,
        MarketScopeMode scopeMode,
        TradeabilityEvidenceProfile tradeabilityEvidenceProfile
) {
    public CreateActiveScanCommand {
        if (scopeMode == null && requestedMarketIds != null
                && requestedMarketIds.stream().anyMatch(java.util.Objects::nonNull)) {
            scopeMode = MarketScopeMode.SELECTED;
        }
        if (tradeabilityEvidenceProfile == null) {
            tradeabilityEvidenceProfile = TradeabilityEvidenceProfile.UNSUPPORTED;
        }
    }

    public CreateActiveScanCommand(UUID actorId, String idempotencyKey, UUID accountId,
                                   String objective, List<UUID> requestedMarketIds) {
        this(actorId, idempotencyKey, accountId, null, objective, requestedMarketIds,
                requestedMarketIds != null && requestedMarketIds.stream().anyMatch(java.util.Objects::nonNull)
                        ? MarketScopeMode.SELECTED : null, TradeabilityEvidenceProfile.UNSUPPORTED);
    }

    public CreateActiveScanCommand(UUID actorId, String idempotencyKey, UUID accountId,
                                   String objective, List<UUID> requestedMarketIds,
                                   MarketScopeMode scopeMode) {
        this(actorId, idempotencyKey, accountId, null, objective, requestedMarketIds, scopeMode,
                TradeabilityEvidenceProfile.UNSUPPORTED);
    }

    public CreateActiveScanCommand(UUID actorId, String idempotencyKey, UUID accountId,
                                   String objective, List<UUID> requestedMarketIds,
                                   MarketScopeMode scopeMode,
                                   TradeabilityEvidenceProfile tradeabilityEvidenceProfile) {
        this(actorId, idempotencyKey, accountId, null, objective, requestedMarketIds, scopeMode,
                tradeabilityEvidenceProfile);
    }
}
