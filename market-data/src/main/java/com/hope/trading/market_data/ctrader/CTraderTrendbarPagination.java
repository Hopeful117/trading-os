package com.hope.trading.market_data.ctrader;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class CTraderTrendbarPagination {
    private CTraderTrendbarPagination() { }

    static List<CTraderOpenApiClient.CTraderTrendbar> collect(
            Instant from, Instant to, int count, int maxPages, int maxRows,
            Function<PageRequest, CTraderOpenApiClient.CTraderTrendbarPage> fetch) {
        if (from == null || to == null || !from.isBefore(to) || count < 1
                || maxPages < 1 || maxRows < 1) throw new IllegalArgumentException("invalid trendbar bounds");
        Map<Long, CTraderOpenApiClient.CTraderTrendbar> unique = new HashMap<>();
        Instant pageTo = to;
        boolean hasMore = true;
        int pages = 0;
        while (hasMore && unique.size() < Math.min(count, maxRows)) {
            if (++pages > maxPages) throw new IllegalStateException("cTrader trendbar page bound exceeded");
            int remaining = Math.min(count, maxRows) - unique.size();
            CTraderOpenApiClient.CTraderTrendbarPage page = fetch.apply(new PageRequest(from, pageTo, remaining));
            if (page == null) throw new IllegalStateException("cTrader trendbar response is missing");
            for (CTraderOpenApiClient.CTraderTrendbar bar : page.trendbars()) {
                if (bar == null) throw new IllegalStateException("cTrader trendbar is missing");
                Instant open = Instant.ofEpochSecond(bar.timestamp());
                if (open.isBefore(from) || !open.isBefore(to)) continue;
                CTraderOpenApiClient.CTraderTrendbar previous = unique.putIfAbsent(bar.timestamp(), bar);
                if (previous != null && !previous.equals(bar)) {
                    throw new IllegalStateException("conflicting cTrader trendbar timestamp");
                }
            }
            hasMore = page.hasMore();
            if (!hasMore || page.trendbars().isEmpty()) break;
            long oldest = page.trendbars().stream().mapToLong(CTraderOpenApiClient.CTraderTrendbar::timestamp).min().orElseThrow();
            Instant nextPageTo = Instant.ofEpochSecond(oldest).minusNanos(1);
            if (!nextPageTo.isBefore(pageTo)) throw new IllegalStateException("cTrader trendbar pagination did not advance");
            pageTo = nextPageTo;
        }
        return unique.values().stream().sorted(Comparator.comparingLong(CTraderOpenApiClient.CTraderTrendbar::timestamp))
                .limit(Math.min(count, maxRows)).toList();
    }

    record PageRequest(Instant from, Instant to, int count) { }
}
