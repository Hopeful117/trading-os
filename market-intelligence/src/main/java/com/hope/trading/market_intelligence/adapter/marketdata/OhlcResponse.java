package com.hope.trading.market_intelligence.adapter.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OhlcResponse(
        UUID marketId,
        String provider,
        String symbol,
        String interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal vwap,
        Integer trades,
        boolean closed,
        Instant occurredAt,
        boolean synthetic,
        String sourceId,
        Instant fetchedAt,
        UUID observationId,
        Instant capturedAt,
        Instant knownAt,
        String observationFingerprint,
        String normalizationVersion,
        String origin
) {
    public OhlcResponse(
            UUID marketId,
            String provider,
            String symbol,
            String interval,
            Instant openTime,
            Instant closeTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume,
            BigDecimal vwap,
            Integer trades,
            boolean closed,
            Instant occurredAt,
            boolean synthetic,
            String sourceId,
            Instant fetchedAt
    ) {
        this(marketId, provider, symbol, interval, openTime, closeTime, open, high, low, close,
                volume, vwap, trades, closed, occurredAt, synthetic, sourceId, fetchedAt,
                null, null, fetchedAt, null, null, null);
    }

    public OhlcResponse(
            UUID marketId,
            String provider,
            String symbol,
            String interval,
            Instant openTime,
            Instant closeTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume,
            BigDecimal vwap,
            Integer trades,
            boolean closed,
            Instant occurredAt
    ) {
        this(marketId, provider, symbol, interval, openTime, closeTime, open, high,
                low, close, volume, vwap, trades, closed, occurredAt, false,
                null, occurredAt, null, null, occurredAt, null, null, null);
    }
}
