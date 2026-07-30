package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public final class TradePlanReplanningService {
    private final TradePlanRepository plans;
    private final TradingContextRepository contexts;
    private final TradePlanApplicationService service;
    public TradePlanReplanningService(
            TradePlanRepository plans, TradingContextRepository contexts,
            TradePlanApplicationService service) {
        this.plans = plans; this.contexts = contexts; this.service = service;
    }
    public TradePlanningResult replan(
            TradePlanId id, UUID actorId, BigDecimal marketPrice,
            PlanningPreferences preferences, String reason) {
        TradePlan current = plans.findLatest(id)
                .orElseThrow(() -> new NoSuchElementException("TradePlan not found"));
        TradingContext context = contexts.findLatest(current.tradingContext().id())
                .orElseThrow(() -> new NoSuchElementException("Trading Context not found"));
        Set<OpportunityId> opportunityIds = current.rationale().opportunities().stream()
                .map(reference -> reference.id()).collect(Collectors.toUnmodifiableSet());
        return service.create(new TradePlanningRequest(
                opportunityIds, context.id(), context.version(), actorId, marketPrice,
                preferences, current.id(), current.version(), reason));
    }
}
