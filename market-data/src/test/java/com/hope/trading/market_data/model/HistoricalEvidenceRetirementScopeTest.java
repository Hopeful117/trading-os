package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoricalEvidenceRetirementScopeTest {
    @Test
    void rejectsBlankProviderSymbol() {
        assertThatThrownBy(() -> new HistoricalEvidenceRetirementScope(
                MarketProvider.KRAKEN, " ", UUID.randomUUID(), 25))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveDepth() {
        assertThatThrownBy(() -> new HistoricalEvidenceRetirementScope(
                MarketProvider.KRAKEN, "PF_XBTUSD", UUID.randomUUID(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
