package com.hope.trading.market_intelligence.domain.artifact;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureArtifactContent;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent;

/** Marker for immutable, domain-normalized artifact payloads. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@class")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DeterministicMeasurements.class, name = "deterministic-measurements"),
        @JsonSubTypes.Type(value = MarketStructureArtifactContent.class, name = "market-structure"),
        @JsonSubTypes.Type(value = TrendContextCapabilityContent.class, name = "trend-context")
})
public interface ArtifactContent {
}
