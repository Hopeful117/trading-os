package com.hope.trading.market_data.model;

import java.util.List;

/** Keeps provider-observed history separate from continuity-normalized history. */
public record MarketHistorySnapshot(
        List<OhlcEvent> observedEvents,
        List<OhlcEvent> normalizedEvents,
        MarketActivityCalculationBasis activityCalculationBasis
) {
    public MarketHistorySnapshot(List<OhlcEvent> observedEvents, List<OhlcEvent> normalizedEvents) {
        this(observedEvents, normalizedEvents, MarketActivityCalculationBasis.VWAP);
    }

    public MarketHistorySnapshot {
        observedEvents = observedEvents == null ? List.of() : List.copyOf(observedEvents);
        normalizedEvents = normalizedEvents == null ? List.of() : List.copyOf(normalizedEvents);
        activityCalculationBasis = activityCalculationBasis == null
                ? MarketActivityCalculationBasis.VWAP : activityCalculationBasis;
    }
}
