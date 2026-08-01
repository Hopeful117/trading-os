package com.hope.trading.market_data.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ValuationSnapshotBatchResponse(
        UUID snapshotId,
        long version,
        String reportingCurrency,
        Instant valuationTimestamp,
        Instant capturedAt,
        String policyVersion,
        String maxObservationAge,
        SnapshotStatus status,
        List<Fact> facts
) {
    public record Fact(
            String type,
            String id,
            UUID marketId,
            String asset,
            ValuationSnapshotBatchRequest.PriceUse priceUse,
            BigDecimal value,
            FactStatus status,
            Source source,
            List<ConversionLeg> conversionLegs
    ) {
    }

    public record Source(
            UUID observationId,
            UUID marketId,
            String provider,
            String symbol,
            String priceType,
            BigDecimal price,
            Instant effectiveAt,
            Instant capturedAt,
            String observationAge
    ) {
    }

    public record ConversionLeg(
            String fromCurrency,
            String toCurrency,
            BigDecimal rate,
            Source source
    ) {
    }

    public enum SnapshotStatus { COMPLETE, INCOMPLETE }

    public enum FactStatus {
        AVAILABLE,
        MARKET_UNAVAILABLE,
        OBSERVATION_UNAVAILABLE,
        STALE,
        PRICE_UNAVAILABLE,
        CONVERSION_UNAVAILABLE
    }
}
