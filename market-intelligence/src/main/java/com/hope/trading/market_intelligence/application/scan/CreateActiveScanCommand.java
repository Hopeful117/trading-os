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
        TradeabilityEvidenceProfile tradeabilityEvidenceProfile,
        UUID sourcePassiveOpportunityId,
        Long sourcePassiveOpportunityVersion
) {
    public CreateActiveScanCommand {
        if ((sourcePassiveOpportunityId == null) != (sourcePassiveOpportunityVersion == null)) {
            throw new ActiveScanException(
                    "PASSIVE_CANDIDATE_REFERENCE_INCOMPLETE",
                    "sourcePassiveOpportunityId and sourcePassiveOpportunityVersion must be provided together",
                    400);
        }
        if (sourcePassiveOpportunityVersion != null && sourcePassiveOpportunityVersion < 1) {
            throw new ActiveScanException(
                    "PASSIVE_CANDIDATE_REFERENCE_INVALID",
                    "sourcePassiveOpportunityVersion must be positive",
                    400);
        }
        if (scopeMode == null && requestedMarketIds != null
                && requestedMarketIds.stream().anyMatch(java.util.Objects::nonNull)) {
            scopeMode = MarketScopeMode.SELECTED;
        }
        if (tradeabilityEvidenceProfile == null) {
            tradeabilityEvidenceProfile = TradeabilityEvidenceProfile.UNSUPPORTED;
        }
    }

    public CreateActiveScanCommand(UUID actorId, String idempotencyKey, UUID accountId,
                                   UUID brokerAccountId, String objective, List<UUID> requestedMarketIds,
                                   MarketScopeMode scopeMode,
                                   TradeabilityEvidenceProfile tradeabilityEvidenceProfile) {
        this(actorId, idempotencyKey, accountId, brokerAccountId, objective, requestedMarketIds, scopeMode,
                tradeabilityEvidenceProfile, null, null);
    }

    public java.util.Optional<com.hope.trading.market_intelligence.domain.scan.PassiveCandidateReference>
            passiveCandidate() {
        return sourcePassiveOpportunityId == null ? java.util.Optional.empty()
                : java.util.Optional.of(new com.hope.trading.market_intelligence.domain.scan.PassiveCandidateReference(
                        sourcePassiveOpportunityId, sourcePassiveOpportunityVersion));
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
