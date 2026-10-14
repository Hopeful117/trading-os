package com.hope.trading.market_data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.time.Duration;

public record ValuationSnapshotBatchRequest(
        @NotBlank String reportingCurrency,
        @NotNull Instant valuationTimestamp,
        List<@Valid Instrument> instruments,
        List<@Valid Asset> assets,
        Duration maxObservationAge,
        Duration acquisitionTimeout,
        String acquisitionMode,
        String decisionBoundary,
        List<AcquisitionOutcome> acquisitionOutcomes
) {
    public ValuationSnapshotBatchRequest(String reportingCurrency, Instant valuationTimestamp,
                                         List<@Valid Instrument> instruments,
                                         List<@Valid Asset> assets) {
        this(reportingCurrency, valuationTimestamp, instruments, assets, null, null, null, null, List.of());
    }

    public ValuationSnapshotBatchRequest {
        instruments = instruments == null ? List.of() : List.copyOf(instruments);
        assets = assets == null ? List.of() : List.copyOf(assets);
        acquisitionOutcomes = acquisitionOutcomes == null ? List.of() : List.copyOf(acquisitionOutcomes);
    }

    public record Instrument(
            @NotBlank String id,
            @NotNull UUID marketId,
            @NotNull PriceUse priceUse
    ) {
    }

    public record Asset(@NotBlank String id, @NotBlank String currency) {
    }

    public record AcquisitionOutcome(UUID marketId, String status) {
    }

    public enum PriceUse {
        LAST,
        CONSERVATIVE_SELL,
        CONSERVATIVE_BUY
    }
}
