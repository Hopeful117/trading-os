package com.hope.trading.market_intelligence.domain.marketstructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record MarketStructureSwing(
        MarketStructureSwingType type, int index,
        Instant pivotTime, BigDecimal price, Instant confirmationTime,
        String pivotSourceId, String confirmationSourceId,
        Instant evidenceFrom, Instant evidenceTo, List<String> evidenceSourceIds,
        List<MarketStructureSourceReference> evidenceSources,
        boolean suppressed, String suppressionReason) {
    public MarketStructureSwing {
        Objects.requireNonNull(type);
        Objects.requireNonNull(pivotTime); Objects.requireNonNull(price);
        Objects.requireNonNull(confirmationTime); Objects.requireNonNull(pivotSourceId);
        Objects.requireNonNull(confirmationSourceId);
        Objects.requireNonNull(evidenceFrom); Objects.requireNonNull(evidenceTo);
        evidenceSourceIds = List.copyOf(evidenceSourceIds);
        evidenceSources = List.copyOf(evidenceSources);
    }
}
