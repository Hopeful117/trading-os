package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record HistoricalOhlcBackfillPlan(
        UUID marketId,
        String canonicalSymbol,
        MarketProvider provider,
        OhlcInterval interval,
        List<HistoricalOhlcBackfillWindow> windows,
        int estimatedCandleCount,
        int estimatedProviderRequests,
        int estimatedProviderPages,
        String confidence,
        boolean executionAuthorized
) {
    public HistoricalOhlcBackfillPlan {
        if (marketId == null || canonicalSymbol == null || canonicalSymbol.isBlank()
                || provider == null || interval == null || confidence == null || confidence.isBlank()) {
            throw new IllegalArgumentException("Backfill plan identity is required");
        }
        windows = List.copyOf(windows == null ? List.of() : windows);
        if (estimatedCandleCount < 0 || estimatedProviderRequests < 0 || estimatedProviderPages < 0) {
            throw new IllegalArgumentException("Backfill estimates cannot be negative");
        }
        if (executionAuthorized) {
            throw new IllegalArgumentException("Backfill plans are planning-only");
        }
    }

    public String fingerprint() {
        StringBuilder material = new StringBuilder("historical-ohlc-backfill-plan-v1|")
                .append(marketId).append('|').append(canonicalSymbol).append('|').append(provider).append('|')
                .append(interval).append('|').append(estimatedCandleCount).append('|')
                .append(estimatedProviderRequests).append('|').append(estimatedProviderPages).append('|')
                .append(confidence).append('|');
        windows.forEach(window -> material.append(window.fromInclusive()).append(':')
                .append(window.toExclusive()).append(':').append(window.estimatedCandleCount()).append('|'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
