package com.hope.trading.market_data.ctrader;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CTraderTrendbarPaginationTest {
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = FROM.plusSeconds(300);

    @Test
    void followsHasMoreWithBoundedDeterministicOrderingAndCutoffFiltering() {
        AtomicInteger calls = new AtomicInteger();
        List<CTraderOpenApiClient.CTraderTrendbar> bars = CTraderTrendbarPagination.collect(
                FROM, TO, 3, 3, 3, Duration.ofSeconds(1), request -> {
                    calls.incrementAndGet();
                    if (request.to().equals(TO)) return page(true, bar(240), bar(180));
                    assertEquals(FROM.plusSeconds(179), request.to());
                    return page(false, bar(60), bar(120));
                });

        assertEquals(List.of(60L, 120L, 180L), bars.stream()
                .map(CTraderOpenApiClient.CTraderTrendbar::timestamp)
                .map(value -> value - FROM.getEpochSecond()).toList());
        assertEquals(2, calls.get());
    }

    @Test
    void rejectsConflictingDuplicateAndDoesNotExceedPageBound() {
        assertThrows(IllegalStateException.class, () -> CTraderTrendbarPagination.collect(
                FROM, TO, 2, 2, 2, Duration.ofSeconds(1), request -> page(true, bar(60),
                        new CTraderOpenApiClient.CTraderTrendbar(FROM.getEpochSecond() + 60, 9, 2, 1, 2, 1))));
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> CTraderTrendbarPagination.collect(
                FROM, TO, 3, 2, 3, Duration.ofSeconds(1), request ->
                        page(true, bar(60 + calls.getAndIncrement() * 60L))));
    }

    @Test
    void stopsWhenProviderHasMoreHistoryOutsideRequestedRange() {
        List<CTraderOpenApiClient.CTraderTrendbar> bars = CTraderTrendbarPagination.collect(
                FROM, TO, 2, 2, 2, Duration.ofSeconds(1), request -> page(true, bar(60)));

        assertEquals(List.of(60L), bars.stream()
                .map(CTraderOpenApiClient.CTraderTrendbar::timestamp)
                .map(value -> value - FROM.getEpochSecond()).toList());
    }

    private static CTraderOpenApiClient.CTraderTrendbarPage page(boolean more,
                                                                  CTraderOpenApiClient.CTraderTrendbar... bars) {
        return new CTraderOpenApiClient.CTraderTrendbarPage(List.of(bars), more);
    }

    private static CTraderOpenApiClient.CTraderTrendbar bar(long seconds) {
        return new CTraderOpenApiClient.CTraderTrendbar(FROM.getEpochSecond() + seconds, 1, 2, 0, 1, 1);
    }
}
