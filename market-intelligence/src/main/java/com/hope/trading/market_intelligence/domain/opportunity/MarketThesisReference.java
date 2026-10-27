package com.hope.trading.market_intelligence.domain.opportunity;

import java.util.Objects;
import java.util.UUID;

/** Immutable identity of the exact Market Thesis used by an Opportunity. */
public record MarketThesisReference(UUID thesisId, long thesisVersion) {
    public MarketThesisReference {
        Objects.requireNonNull(thesisId, "thesisId");
        if (thesisVersion < 1) throw new IllegalArgumentException("thesisVersion starts at 1");
    }
}
