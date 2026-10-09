package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.math.BigDecimal;
import java.util.*;

public record TradePlanningRequest(
        Set<OpportunityId> opportunityIds, UUID planningContextId, long contextVersion,
        UUID actorId, BigDecimal marketPrice,
        TradePlanId predecessorId, TradePlanVersion predecessorVersion, String replanningReason,
        Map<OpportunityId, OpportunityVersion> exactOpportunityVersions
) {
    public TradePlanningRequest(
            Set<OpportunityId> opportunityIds, UUID planningContextId, long contextVersion,
            UUID actorId, BigDecimal marketPrice, TradePlanId predecessorId,
            TradePlanVersion predecessorVersion, String replanningReason) {
        this(opportunityIds, planningContextId, contextVersion, actorId, marketPrice,
                predecessorId, predecessorVersion, replanningReason, Map.of());
    }

    public TradePlanningRequest {
        opportunityIds = Set.copyOf(opportunityIds);
        exactOpportunityVersions = Map.copyOf(exactOpportunityVersions);
        if (!exactOpportunityVersions.isEmpty()
                && !exactOpportunityVersions.keySet().equals(opportunityIds)) {
            throw new IllegalArgumentException("Exact opportunity references must match opportunity IDs");
        }
        if (opportunityIds.isEmpty()) throw new IllegalArgumentException("Opportunity is required");
        Objects.requireNonNull(planningContextId); Objects.requireNonNull(actorId);
        if (contextVersion < 1) throw new IllegalArgumentException("Context version starts at 1");
        if (marketPrice == null || marketPrice.signum() <= 0) {
            throw new IllegalArgumentException("marketPrice must be positive");
        }
        if ((predecessorId == null) != (predecessorVersion == null)) {
            throw new IllegalArgumentException("Complete predecessor reference is required");
        }
        replanningReason = replanningReason == null ? "" : replanningReason.trim();
    }

    public static TradePlanningRequest withExactOpportunityVersion(
            OpportunityId opportunityId, OpportunityVersion opportunityVersion,
            UUID planningContextId, long contextVersion, UUID actorId,
            BigDecimal marketPrice, TradePlanId predecessorId,
            TradePlanVersion predecessorVersion, String replanningReason) {
        return new TradePlanningRequest(Set.of(opportunityId), planningContextId,
                contextVersion, actorId, marketPrice, predecessorId,
                predecessorVersion, replanningReason,
                Map.of(opportunityId, opportunityVersion));
    }
}
