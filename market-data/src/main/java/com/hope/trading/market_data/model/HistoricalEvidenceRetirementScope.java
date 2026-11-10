package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.util.Objects;
import java.util.UUID;

public record HistoricalEvidenceRetirementScope(
        MarketProvider provider,
        String providerSymbol,
        UUID marketId,
        int depth
) {
    public HistoricalEvidenceRetirementScope {
        Objects.requireNonNull(provider, "provider is required");
        Objects.requireNonNull(providerSymbol, "providerSymbol is required");
        Objects.requireNonNull(marketId, "marketId is required");
        if (providerSymbol.isBlank() || depth < 1) {
            throw new IllegalArgumentException("providerSymbol and positive depth are required");
        }
    }
}
