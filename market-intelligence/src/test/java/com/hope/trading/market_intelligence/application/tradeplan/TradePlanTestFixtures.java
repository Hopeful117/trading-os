package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.adapter.ai.DisabledAiTradePlanningAdapter;
import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class TradePlanTestFixtures {
    public static final Instant NOW = Instant.parse("2026-07-30T14:00:00Z");
    public static TradingContext context(UUID id, long version, UUID owner) {
        return new TradingContext(
                id, version, NOW, owner, UUID.randomUUID(), "EUR",
                BigDecimal.valueOf(10_000), BigDecimal.valueOf(20_000),
                BigDecimal.valueOf(2), "CONSERVATIVE", "STANDARD",
                Map.of("BTC/EUR", BigDecimal.ZERO), Map.of("orderType", "LIMIT"));
    }
    public static TradingOpportunity activeOpportunity() {
        return OpportunityTestFixtures.opportunity(
                new OpportunityId(UUID.randomUUID()), 1, OpportunityStatus.ACTIVE,
                new OpportunityScore(BigDecimal.valueOf(80)), NOW);
    }
    public static PlanningPolicyRegistry policies() {
        return new PlanningPolicyRegistry(List.of(
                new DefaultPlanningPolicies.EntrySelection(),
                new DefaultPlanningPolicies.StopSelection(),
                new DefaultPlanningPolicies.TargetSelection(),
                new DefaultPlanningPolicies.PositionSizingSelection(),
                new DefaultPlanningPolicies.ExpirationSelection(),
                new DefaultPlanningPolicies.ConfirmationSelection(),
                new DefaultPlanningPolicies.InvalidationSelection(),
                new DefaultPlanningPolicies.ManagementSelection(),
                new DefaultPlanningPolicies.ThesisSelection()));
    }
    public static Environment environment() {
        UUID owner = UUID.randomUUID();
        TradingOpportunity opportunity = activeOpportunity();
        var opportunityStore = new InMemoryTradingOpportunityRepository();
        opportunityStore.append(opportunity);
        var contextStore = new InMemoryTradingContextRepository();
        TradingContext context = context(UUID.randomUUID(), 1, owner);
        contextStore.saveSnapshot(context);
        var plans = new InMemoryTradePlanRepository();
        var engine = new TradePlanningEngine(
                opportunityStore, contextStore, (actor, value) -> actor.equals(value.ownerId()),
                plans, policies(), new DisabledAiTradePlanningAdapter(),
                new AiContributionValidator(), new TradePlanFactory(),
                () -> new TradePlanId(UUID.randomUUID()), Clock.fixed(NOW, ZoneOffset.UTC));
        var events = new ArrayList<com.hope.trading.market_intelligence.domain.tradeplan.TradePlanEvent>();
        var metrics = new com.hope.trading.market_intelligence.adapter.observability
                .InMemoryTradePlanningMetrics();
        var service = new TradePlanApplicationService(
                engine, plans, new TradePlanLifecyclePolicy(), events::add, metrics,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Environment(
                owner, opportunity, context, opportunityStore, contextStore, plans,
                engine, service, events, metrics);
    }
    public static TradePlanningRequest request(Environment environment) {
        return new TradePlanningRequest(
                Set.of(environment.opportunity().id()), environment.context().id(),
                environment.context().version(), environment.owner(), BigDecimal.valueOf(100),
                PlanningPreferences.conservative(), null, null, "");
    }
    public record Environment(
            UUID owner, TradingOpportunity opportunity, TradingContext context,
            InMemoryTradingOpportunityRepository opportunities,
            InMemoryTradingContextRepository contexts,
            InMemoryTradePlanRepository plans, TradePlanningEngine engine,
            TradePlanApplicationService service,
            List<com.hope.trading.market_intelligence.domain.tradeplan.TradePlanEvent> events,
            com.hope.trading.market_intelligence.adapter.observability
                    .InMemoryTradePlanningMetrics metrics) {}
    private TradePlanTestFixtures() {}
}
