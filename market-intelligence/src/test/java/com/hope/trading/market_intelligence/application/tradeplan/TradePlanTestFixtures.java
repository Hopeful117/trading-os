package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.adapter.ai.DisabledAiTradePlanningAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class TradePlanTestFixtures {
    public static final Instant NOW = Instant.parse("2026-07-30T14:00:00Z");
    public static TradePlanningContext context(UUID id, long version, UUID owner) {
        return context(id, version, owner, UUID.randomUUID());
    }
    public static TradePlanningContext context(UUID id, long version, UUID owner, UUID accountId) {
        return new TradePlanningContext(id, version, NOW, owner, accountId, "EUR",
                new RiskBudget(BigDecimal.valueOf(100), "EUR", UUID.randomUUID(), 3), preferences());
    }
    public static PlanningPreferences preferences() {
        return new PlanningPreferences(UUID.randomUUID(), 2, EntryType.LIMIT,
                PlanningPreferences.StopStrategy.PERCENTAGE_DISTANCE, BigDecimal.ONE,
                PlanningPreferences.TargetStrategy.RISK_MULTIPLE, BigDecimal.valueOf(2),
                PlanningPreferences.PlanningHorizon.INTRADAY, Duration.ofHours(1));
    }
    public static TradingOpportunity activeOpportunity() {
        return OpportunityTestFixtures.opportunity(
                new OpportunityId(UUID.randomUUID()), 1, OpportunityStatus.ACTIVE,
                new OpportunityScore(BigDecimal.valueOf(80)), NOW);
    }
    public static TradingOpportunity activeOpportunity(UUID accountId) {
        UUID marketId = UUID.randomUUID();
        return new OpportunityFactory().create(new OpportunityFactory.Values(
                new OpportunityId(UUID.randomUUID()), new OpportunityVersion(1), OpportunityStatus.ACTIVE,
                "BTC/EUR", OpportunityDirection.LONG, "Bullish breakout", "5m",
                OpportunityType.SCALPING, OpportunityOrigin.USER_REQUEST,
                new OpportunityScore(BigDecimal.valueOf(80)), "Confirmed",
                Set.of(new ObservationReference(UUID.randomUUID())), Set.of(), NOW, NOW,
                NOW.plusSeconds(300), NOW, UUID.randomUUID(), null, marketId, accountId,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    }
    public static TradingOpportunity accountlessNextVersion(TradingOpportunity source) {
        return new OpportunityFactory().create(new OpportunityFactory.Values(
                source.id(), source.version().next(), source.status(), source.instrument(),
                source.direction(), source.scenario(), source.timeframe(), source.type(),
                source.origin(), source.score(), source.explanation(), source.observations(),
                source.aiAnalyses(), source.evaluatedAt(), source.validFrom(),
                source.validUntil().orElse(null), source.createdAt(),
                source.strategyMatchId().orElse(null), source.setup().orElse(null),
                source.marketId().orElse(null), null, null, null, null));
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
        TradePlanningContext context = context(UUID.randomUUID(), 1, owner);
        TradingOpportunity opportunity = activeOpportunity(context.tradingAccountId());
        var opportunityStore = new InMemoryTradingOpportunityRepository();
        opportunityStore.append(opportunity);
        var contextStore = new InMemoryTradePlanningContextRepository();
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
        var idempotency = new InMemoryManualTradePlanIdempotencyRepository();
        var fingerprints = new ManualTradePlanFingerprintFactory(
                new ObjectMapper().findAndRegisterModules());
        var creation = new ManualTradePlanCreationTransaction(
                engine, plans, idempotency, events::add, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
        var service = new TradePlanApplicationService(
                engine, plans, new TradePlanLifecyclePolicy(), events::add, metrics,
                Clock.fixed(NOW, ZoneOffset.UTC),
                idempotency, fingerprints, creation);
        return new Environment(
                owner, opportunity, context, opportunityStore, contextStore, plans,
                engine, service, idempotency, events, metrics);
    }
    public static TradePlanningRequest request(Environment environment) {
        return new TradePlanningRequest(
                Set.of(environment.opportunity().id()), environment.context().id(),
                environment.context().version(), environment.owner(), BigDecimal.valueOf(100),
                null, null, "");
    }
    public record Environment(
            UUID owner, TradingOpportunity opportunity, TradePlanningContext context,
            InMemoryTradingOpportunityRepository opportunities,
            InMemoryTradePlanningContextRepository contexts,
            InMemoryTradePlanRepository plans, TradePlanningEngine engine,
            TradePlanApplicationService service, InMemoryManualTradePlanIdempotencyRepository idempotency,
            List<com.hope.trading.market_intelligence.domain.tradeplan.TradePlanEvent> events,
            com.hope.trading.market_intelligence.adapter.observability
                    .InMemoryTradePlanningMetrics metrics) {}
    private TradePlanTestFixtures() {}
}
