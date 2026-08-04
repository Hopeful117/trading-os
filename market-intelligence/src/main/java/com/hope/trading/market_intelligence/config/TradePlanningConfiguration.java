package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.adapter.ai.DisabledAiTradePlanningAdapter;
import com.hope.trading.market_intelligence.adapter.observability.*;
import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.application.tradeplan.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import org.springframework.context.annotation.*;
import java.time.Clock;
import java.util.*;

@Configuration
public class TradePlanningConfiguration {
    @Bean TradePlanningContextAccessPolicy tradePlanningContextAccessPolicy() {
        return (actor, context) -> context.ownerId().equals(actor);
    }
    @Bean PlanningPolicyRegistry planningPolicyRegistry() {
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
    @Bean AiTradePlanningPort aiTradePlanningPort() {
        return new DisabledAiTradePlanningAdapter();
    }
    @Bean AiContributionValidator aiContributionValidator() {
        return new AiContributionValidator();
    }
    @Bean TradePlanFactory tradePlanFactory() { return new TradePlanFactory(); }
    @Bean TradePlanIdentifierGenerator tradePlanIdentifierGenerator() {
        return () -> new TradePlanId(UUID.randomUUID());
    }
    @Bean TradePlanningEngine tradePlanningEngine(
            TradingOpportunityRepository opportunities, TradePlanningContextRepository contexts,
            TradePlanningContextAccessPolicy access, TradePlanRepository plans,
            PlanningPolicyRegistry policies, AiTradePlanningPort ai,
            AiContributionValidator aiValidator, TradePlanFactory factory,
            TradePlanIdentifierGenerator identifiers, Clock clock) {
        return new TradePlanningEngine(
                opportunities, contexts, access, plans, policies, ai, aiValidator,
                factory, identifiers, clock);
    }
    @Bean TradePlanLifecyclePolicy tradePlanLifecyclePolicy() {
        return new TradePlanLifecyclePolicy();
    }
    @Bean TradePlanEventPublisher tradePlanEventPublisher() {
        return new LoggingTradePlanEventPublisher();
    }
    @Bean TradePlanningMetrics tradePlanningMetrics() {
        return new InMemoryTradePlanningMetrics();
    }
    @Bean TradePlanApplicationService tradePlanApplicationService(
            TradePlanningEngine engine, TradePlanRepository repository,
            TradePlanLifecyclePolicy lifecycle, TradePlanEventPublisher events,
            TradePlanningMetrics metrics, Clock clock) {
        return new TradePlanApplicationService(
                engine, repository, lifecycle, events, metrics, clock);
    }
    @Bean TradePlanReplanningService tradePlanReplanningService(
            TradePlanRepository plans, TradePlanningContextRepository contexts,
            TradePlanApplicationService service) {
        return new TradePlanReplanningService(plans, contexts, service);
    }
    @Bean DefaultTradePlanIntegrationBoundary tradePlanIntegrationBoundary(
            TradePlanRepository repository, TradePlanApplicationService service) {
        return new DefaultTradePlanIntegrationBoundary(repository, service);
    }
    @Bean TradePlanRiskHandoffService tradePlanRiskHandoffService(
            TradePlanRepository plans, TradePlanningContextRepository contexts,
            TradePlanRiskValidationBoundary lifecycle,
            RiskValidationAcknowledgmentRepository acknowledgments, Clock clock) {
        return new TradePlanRiskHandoffService(
                plans, contexts, lifecycle, acknowledgments, clock, UUID::randomUUID);
    }
}
