package com.hope.trading.market_intelligence.configuration;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hope.trading.market_intelligence.adapter.marketdata.TrendContextRoleHistory;
import com.hope.trading.market_intelligence.domain.HistoricalOhlcContext;
import com.hope.trading.market_intelligence.domain.MarketIdentityContext;
import com.hope.trading.market_intelligence.domain.MarketSnapshotContext;
import com.hope.trading.market_intelligence.domain.NewsContext;
import com.hope.trading.market_intelligence.domain.artifact.DeterministicMeasurements;
import com.hope.trading.market_intelligence.domain.capability.ProducedContribution;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureArtifactContent;
import com.hope.trading.market_intelligence.domain.observation.TrendContextObservationPayload;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonPolymorphismConfiguration {
    @Bean
    Module marketIntelligencePolymorphicTypes() {
        SimpleModule module = new SimpleModule("market-intelligence-polymorphic-types");
        module.registerSubtypes(aliases(DeterministicMeasurements.class, "deterministic-measurements"));
        module.registerSubtypes(aliases(MarketStructureArtifactContent.class, "market-structure"));
        module.registerSubtypes(aliases(TrendContextCapabilityContent.class, "trend-context"));
        module.registerSubtypes(aliases(HistoricalOhlcContext.class, "historical-ohlc"));
        module.registerSubtypes(aliases(NewsContext.class, "news"));
        module.registerSubtypes(aliases(MarketSnapshotContext.class, "market-snapshot"));
        module.registerSubtypes(aliases(MarketIdentityContext.class, "market-identity"));
        module.registerSubtypes(aliases(TrendContextRoleHistory.class, "trend-context-role-history"));
        module.registerSubtypes(aliases(TrendContextObservationPayload.class, "trend-context-observation"));
        module.registerSubtypes(aliases(ProducedContribution.ArtifactContribution.class, "artifact-contribution"));
        module.registerSubtypes(aliases(ProducedContribution.ObservationContribution.class, "observation-contribution"));
        module.registerSubtypes(aliases(ProducedContribution.MetricContribution.class, "metric-contribution"));
        module.registerSubtypes(aliases(ProducedContribution.RecommendationContribution.class, "recommendation-contribution"));
        return module;
    }

    private NamedType[] aliases(Class<?> type, String logicalName) {
        return new NamedType[]{new NamedType(type, logicalName), new NamedType(type, type.getName())};
    }
}
