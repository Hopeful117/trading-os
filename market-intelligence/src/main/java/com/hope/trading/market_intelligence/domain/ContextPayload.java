package com.hope.trading.market_intelligence.domain;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactContent;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@class")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HistoricalOhlcContext.class, name = "historical-ohlc"),
        @JsonSubTypes.Type(value = NewsContext.class, name = "news"),
        @JsonSubTypes.Type(value = MarketSnapshotContext.class, name = "market-snapshot"),
        @JsonSubTypes.Type(value = MarketIdentityContext.class, name = "market-identity")
})
public interface ContextPayload extends ArtifactContent {
}
