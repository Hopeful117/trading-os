package com.hope.trading.market_intelligence.application.research;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoricalReconstructionPolicyTest {
    @Test
    void acceptsOnlyTheCompleteDeterministicDependencyClosure() {
        HistoricalReconstructionPolicy.requireAllowed(
                Set.of("trend-context-analysis", "market-structure-analysis",
                        "ohlc-range-analysis", "ohlc-momentum-analysis"));
    }

    @Test
    void rejectsAClosureWithoutMarketStructureProducer() {
        assertThatThrownBy(() -> HistoricalReconstructionPolicy.requireAllowed(
                Set.of("trend-context-analysis", "ohlc-range-analysis", "ohlc-momentum-analysis")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnlistedCapabilities() {
        assertThatThrownBy(() -> HistoricalReconstructionPolicy.requireAllowed(
                Set.of("trend-context-analysis", "market-structure-analysis",
                        "ohlc-range-analysis", "ohlc-momentum-analysis", "news-context")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
