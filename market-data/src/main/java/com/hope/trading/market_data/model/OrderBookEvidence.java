package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Comparator;

public record OrderBookEvidence(
        UUID evidenceId,
        UUID marketId,
        MarketProvider provider,
        String symbol,
        String providerSymbol,
        int depth,
        OrderBookEvidenceType evidenceType,
        OrderBookEvidenceOrigin origin,
        Long sourceStartOrdinal,
        Long sourceEndOrdinal,
        UUID sequenceId,
        long sequenceOrdinal,
        Long providerSequence,
        Instant occurredAt,
        Instant knownAt,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        Long checksum,
        String normalizationVersion,
        String contentFingerprint
) {
    public OrderBookEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId is required");
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(provider, "provider is required");
        Objects.requireNonNull(symbol, "symbol is required");
        Objects.requireNonNull(evidenceType, "evidenceType is required");
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(sequenceId, "sequenceId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(knownAt, "knownAt is required");
        Objects.requireNonNull(bids, "bids is required");
        Objects.requireNonNull(asks, "asks is required");
        Objects.requireNonNull(normalizationVersion, "normalizationVersion is required");
        Objects.requireNonNull(contentFingerprint, "contentFingerprint is required");
        if (depth < 1 || sequenceOrdinal < 0) {
            throw new IllegalArgumentException("depth and sequenceOrdinal must be non-negative");
        }
        if (knownAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("knownAt cannot precede occurredAt");
        }
        validateLevels(bids);
        validateLevels(asks);
        bids = bids.stream().sorted(Comparator.comparing(OrderBookLevel::price).reversed()).toList();
        asks = asks.stream().sorted(Comparator.comparing(OrderBookLevel::price)).toList();
    }

    private static void validateLevels(List<OrderBookLevel> levels) {
        for (OrderBookLevel level : levels) {
            if (level == null || level.price() == null || level.quantity() == null
                    || level.price().signum() <= 0 || level.quantity().signum() < 0) {
                throw new IllegalArgumentException("Order-book evidence contains an invalid level");
            }
        }
    }
}
