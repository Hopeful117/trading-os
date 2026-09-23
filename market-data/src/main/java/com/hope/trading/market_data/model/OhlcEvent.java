package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

public record OhlcEvent(
        UUID marketId,
        MarketProvider provider,
        String symbol,
        OhlcInterval interval,
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
) implements MarketEvent {
    public OhlcEvent(
            UUID marketId,
            MarketProvider provider,
            String symbol,
            OhlcInterval interval,
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
        this(
                marketId,
                provider,
                symbol,
                interval,
                openTime,
                closeTime,
                open,
                high,
                low,
                close,
                volume,
                vwap,
                trades,
                closed,
                occurredAt,
                false,
                defaultSourceId(marketId, provider, symbol, interval, openTime),
                occurredAt
        );
    }

    public OhlcEvent {
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(provider, "provider is required");
        Objects.requireNonNull(symbol, "symbol is required");
        Objects.requireNonNull(interval, "interval is required");
        Objects.requireNonNull(openTime, "openTime is required");
        Objects.requireNonNull(closeTime, "closeTime is required");
        Objects.requireNonNull(sourceId, "sourceId is required");
        Objects.requireNonNull(fetchedAt, "fetchedAt is required");
    }

    public static String defaultSourceId(
            UUID marketId,
            MarketProvider provider,
            String symbol,
            OhlcInterval interval,
            Instant openTime
    ) {
        return provider + ":" + marketId + ":" + symbol + ":"
                + interval + ":" + openTime;
    }

    @Override
    public MarketStreamType streamType() {
        return MarketStreamType.OHLC;
    }
}
