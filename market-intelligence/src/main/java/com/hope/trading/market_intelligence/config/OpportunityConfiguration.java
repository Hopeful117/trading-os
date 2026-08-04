package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.adapter.ai.ReadOnlyAiAnalysisCatalog;
import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.application.opportunity.*;
import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import org.springframework.context.annotation.*;

import java.time.*;
import java.util.UUID;

@Configuration
public class OpportunityConfiguration {
    @Bean UserOpportunityRepository userOpportunityRepository() {
        return new InMemoryUserOpportunityRepository();
    }
    @Bean AiAnalysisCatalog aiAnalysisCatalog() { return new ReadOnlyAiAnalysisCatalog(); }
    @Bean OpportunityFusionPolicy opportunityFusionPolicy() {
        return new DeterministicOpportunityFusionPolicy();
    }
    @Bean OpportunityDeduplicationPolicy opportunityDeduplicationPolicy() {
        return new OpportunityDeduplicationPolicy(Duration.ofMinutes(15));
    }
    @Bean OpportunityLifecyclePolicy opportunityLifecyclePolicy() {
        return new OpportunityLifecyclePolicy();
    }
    @Bean OpportunityFactory opportunityFactory() { return new OpportunityFactory(); }
    @Bean OpportunityIdentifierGenerator opportunityIdentifierGenerator() {
        return () -> new OpportunityId(UUID.randomUUID());
    }
    @Bean OpportunityEngine opportunityEngine(
            ObservationRepository observations, AiAnalysisCatalog ai,
            TradingOpportunityRepository repository, OpportunityFusionPolicy fusion,
            OpportunityDeduplicationPolicy deduplication,
            OpportunityLifecyclePolicy lifecycle, OpportunityFactory factory,
            OpportunityIdentifierGenerator identifiers, Clock clock) {
        return new OpportunityEngine(
                observations, ai, repository, fusion, deduplication, lifecycle,
                factory, identifiers, clock);
    }
    @Bean OpportunityRegistry opportunityRegistry(
            TradingOpportunityRepository repository, OpportunityEngine engine) {
        return new DefaultOpportunityRegistry(repository, engine);
    }
    @Bean OpportunityRankingEngine opportunityRankingEngine() {
        return new OpportunityRankingEngine();
    }
    @Bean OpportunityExpirationPolicy opportunityExpirationPolicy() {
        return new ValidityWindowExpirationPolicy();
    }
    @Bean UserOpportunityService userOpportunityService(
            UserOpportunityRepository projections,
            TradingOpportunityRepository opportunities, Clock clock) {
        return new UserOpportunityService(projections, opportunities, clock);
    }
}
