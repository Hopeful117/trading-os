package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.InstrumentProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoricalFundingEvidence(
        UUID evidenceId,
        UUID marketId,
        MarketProvider provider,
        String symbol,
        String providerSymbol,
        InstrumentProductType productType,
        Instant fundingTime,
        Instant effectiveFrom,
        Instant effectiveTo,
        BigDecimal fundingRate,
        String settlementAsset,
        String sourceId,
        Instant sourceUpdatedAt,
        Instant fetchedAt,
        Instant capturedAt,
        Instant knownAt,
        HistoricalFundingEvidenceOrigin origin,
        String normalizationVersion,
        String evidenceFingerprint
) {
    public HistoricalFundingEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId is required");
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(provider, "provider is required");
        symbol = required(symbol, "symbol");
        providerSymbol = required(providerSymbol, "providerSymbol");
        Objects.requireNonNull(productType, "productType is required");
        Objects.requireNonNull(fundingTime, "fundingTime is required");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        if (effectiveTo != null && !effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("effectiveFrom must precede effectiveTo");
        }
        if (fundingTime.isBefore(effectiveFrom)
                || (effectiveTo != null && !fundingTime.isBefore(effectiveTo))) {
            throw new IllegalArgumentException("fundingTime must be inside the effective interval");
        }
        Objects.requireNonNull(fundingRate, "fundingRate is required");
        settlementAsset = required(settlementAsset, "settlementAsset");
        sourceId = required(sourceId, "sourceId");
        Objects.requireNonNull(fetchedAt, "fetchedAt is required");
        Objects.requireNonNull(capturedAt, "capturedAt is required");
        Objects.requireNonNull(knownAt, "knownAt is required");
        Objects.requireNonNull(origin, "origin is required");
        normalizationVersion = required(normalizationVersion, "normalizationVersion");
        evidenceFingerprint = required(evidenceFingerprint, "evidenceFingerprint");
        if (capturedAt.isBefore(fetchedAt)) {
            throw new IllegalArgumentException("capturedAt cannot precede fetchedAt");
        }
        if (knownAt.isBefore(capturedAt)) {
            throw new IllegalArgumentException("knownAt cannot precede capturedAt");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }
}
