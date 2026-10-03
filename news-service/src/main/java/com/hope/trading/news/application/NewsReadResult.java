package com.hope.trading.news.application;

import com.hope.trading.news.domain.NewsAvailability;

import java.time.Instant;
import java.util.List;

public record NewsReadResult<T>(
        NewsAvailability status,
        List<T> items,
        Instant fetchedAt,
        String message
) {
    public NewsReadResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
