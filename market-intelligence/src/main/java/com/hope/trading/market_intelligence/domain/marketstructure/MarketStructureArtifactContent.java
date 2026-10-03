package com.hope.trading.market_intelligence.domain.marketstructure;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactContent;

import java.time.Instant;
import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record MarketStructureArtifactContent(
        MarketStructureResult result, Instant cutOffAt, String inputFingerprint) implements ArtifactContent {
    public MarketStructureArtifactContent {
        Objects.requireNonNull(result); Objects.requireNonNull(cutOffAt);
        Objects.requireNonNull(inputFingerprint);
    }
}
