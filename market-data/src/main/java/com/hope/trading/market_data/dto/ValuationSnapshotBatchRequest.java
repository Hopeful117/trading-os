package com.hope.trading.market_data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ValuationSnapshotBatchRequest(
        @NotBlank String reportingCurrency,
        @NotNull Instant valuationTimestamp,
        List<@Valid Instrument> instruments,
        List<@Valid Asset> assets
) {
    public ValuationSnapshotBatchRequest {
        instruments = instruments == null ? List.of() : List.copyOf(instruments);
        assets = assets == null ? List.of() : List.copyOf(assets);
    }

    public record Instrument(
            @NotBlank String id,
            @NotNull UUID marketId,
            @NotNull PriceUse priceUse
    ) {
    }

    public record Asset(@NotBlank String id, @NotBlank String currency) {
    }

    public enum PriceUse {
        LAST,
        CONSERVATIVE_SELL,
        CONSERVATIVE_BUY
    }
}
