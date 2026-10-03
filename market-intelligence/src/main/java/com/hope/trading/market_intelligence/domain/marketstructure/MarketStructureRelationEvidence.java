package com.hope.trading.market_intelligence.domain.marketstructure;

import java.util.Objects;

public record MarketStructureRelationEvidence(
        MarketStructureSwingType swingType,
        MarketStructureRelation relation,
        MarketStructureSwing previous,
        MarketStructureSwing latest) {
    public MarketStructureRelationEvidence {
        Objects.requireNonNull(swingType);
        Objects.requireNonNull(relation);
        Objects.requireNonNull(previous);
        Objects.requireNonNull(latest);
        if (previous.type() != swingType || latest.type() != swingType) {
            throw new IllegalArgumentException("Relation swings must have the declared type");
        }
        if (!previous.pivotTime().isBefore(latest.pivotTime())) {
            throw new IllegalArgumentException("Relation swings must be chronological");
        }
    }
}
