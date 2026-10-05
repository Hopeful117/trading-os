package com.hope.trading.market_intelligence.domain.marketstructure;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactContent;

import java.time.Instant;
import java.util.Objects;

public record MarketStructureArtifactContent(
        MarketStructureResult result, Instant cutOffAt, String inputFingerprint) implements ArtifactContent {
    public MarketStructureArtifactContent {
        Objects.requireNonNull(result); Objects.requireNonNull(cutOffAt);
        Objects.requireNonNull(inputFingerprint);
    }
}
