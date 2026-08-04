package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public final class TradePlanReplanningService {
    private final TradePlanRepository plans;
    private final TradePlanningContextRepository contexts;
    private final TradePlanApplicationService service;
    public TradePlanReplanningService(
            TradePlanRepository plans, TradePlanningContextRepository contexts,
            TradePlanApplicationService service) {
        this.plans = plans; this.contexts = contexts; this.service = service;
    }
    public TradePlanningResult replan(
            TradePlanId id, UUID actorId, BigDecimal marketPrice, String reason) {
        TradePlan current = plans.findLatest(id)
                .orElseThrow(() -> new NoSuchElementException("TradePlan not found"));
        TradePlanningContext context = contexts.findLatest(current.planningContext().id())
                .orElseThrow(() -> new NoSuchElementException("Trade Planning Context not found"));
        Set<OpportunityId> opportunityIds = current.rationale().opportunities().stream()
                .map(reference -> reference.id()).collect(Collectors.toUnmodifiableSet());
        return service.create(new TradePlanningRequest(
                opportunityIds, context.id(), context.version(), actorId, marketPrice,
                current.id(), current.version(), reason));
    }
}
