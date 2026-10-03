package com.hope.trading.news.application;

import com.hope.trading.news.domain.ImpactLevel;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

public record NewsQuery(
        Instant from,
        Instant to,
        UUID marketId,
        String currency,
        ImpactLevel impact,
        int limit
) {
    public NewsQuery {
        limit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
        if (from != null && to != null) {
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("News query start must not be after its end");
            }
            if (Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
                throw new IllegalArgumentException("News query window must not exceed 31 days");
            }
        }
    }
}
