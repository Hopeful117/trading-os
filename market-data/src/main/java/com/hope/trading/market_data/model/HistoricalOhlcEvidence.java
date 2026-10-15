package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoricalOhlcEvidence(
        UUID observationId,
        UUID marketId,
        OhlcInterval interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal vwap,
        Integer tradeCount,
        boolean closed,
        boolean synthetic,
        MarketProvider provider,
        String providerSymbol,
        String sourceId,
        Instant sourceUpdatedAt,
        Instant fetchedAt,
        Instant capturedAt,
        Instant knownAt,
        HistoricalOhlcEvidenceOrigin origin,
        String normalizationVersion,
        String observationFingerprint
) {
    public HistoricalOhlcEvidence {
        Objects.requireNonNull(observationId, "observationId is required");
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(interval, "interval is required");
        Objects.requireNonNull(openTime, "openTime is required");
        Objects.requireNonNull(closeTime, "closeTime is required");
        Objects.requireNonNull(open, "open is required");
        Objects.requireNonNull(high, "high is required");
        Objects.requireNonNull(low, "low is required");
        Objects.requireNonNull(close, "close is required");
        Objects.requireNonNull(provider, "provider is required");
        Objects.requireNonNull(sourceId, "sourceId is required");
        Objects.requireNonNull(fetchedAt, "fetchedAt is required");
        Objects.requireNonNull(capturedAt, "capturedAt is required");
        Objects.requireNonNull(knownAt, "knownAt is required");
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(normalizationVersion, "normalizationVersion is required");
        Objects.requireNonNull(observationFingerprint, "observationFingerprint is required");
        if (!openTime.isBefore(closeTime)) {
            throw new IllegalArgumentException("openTime must be before closeTime");
        }
        if (!closeTime.equals(openTime.plus(interval.getDuration()))) {
            throw new IllegalArgumentException("closeTime must match interval");
        }
        if (knownAt.isBefore(fetchedAt)) {
            throw new IllegalArgumentException("knownAt cannot precede fetchedAt");
        }
        validateTemporalOrdering(origin, fetchedAt, capturedAt, knownAt);
        if (open.signum() <= 0 || high.signum() <= 0 || low.signum() <= 0 || close.signum() <= 0) {
            throw new IllegalArgumentException("OHLC prices must be positive");
        }
        if (low.compareTo(open) > 0 || open.compareTo(high) > 0
                || low.compareTo(close) > 0 || close.compareTo(high) > 0) {
            throw new IllegalArgumentException("OHLC prices are inconsistent");
        }
        if (volume != null && volume.signum() < 0) {
            throw new IllegalArgumentException("volume cannot be negative");
        }
        if (tradeCount != null && tradeCount < 0) {
            throw new IllegalArgumentException("tradeCount cannot be negative");
        }
    }

    private static void validateTemporalOrdering(
            HistoricalOhlcEvidenceOrigin origin,
            Instant fetchedAt,
            Instant capturedAt,
            Instant knownAt
    ) {
        if (origin != HistoricalOhlcEvidenceOrigin.CAPTURED) {
            return;
        }
        if (capturedAt.isBefore(fetchedAt)) {
            throw new IllegalArgumentException("capturedAt cannot precede fetchedAt for captured evidence");
        }
        if (knownAt.isBefore(capturedAt)) {
            throw new IllegalArgumentException("knownAt cannot precede capturedAt for captured evidence");
        }
    }
}
