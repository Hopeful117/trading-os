package com.hope.trading.market_data.ctrader;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public interface CTraderOpenApiClient {
    List<CTraderSymbol> symbols();
    CTraderSymbol symbol(long symbolId);
    List<CTraderTrendbar> trendbars(long symbolId, String period, Instant from, Instant to, int count);

    default CTraderTrendbarPage trendbarPage(long symbolId, String period, Instant from, Instant to, int count) {
        return new CTraderTrendbarPage(trendbars(symbolId, period, from, to, count), false);
    }

    default CTraderStream openStream(Consumer<CTraderStreamEvent> events, Runnable termination) {
        throw new UnsupportedOperationException("cTrader live streams are unavailable");
    }

    record CTraderSymbol(long id, String name, int digits, long minVolume, long maxVolume, long stepVolume) { }
    record CTraderTrendbar(long timestamp, long open, long high, long low, long close, long volume) { }
    record CTraderTrendbarPage(List<CTraderTrendbar> trendbars, boolean hasMore) {
        public CTraderTrendbarPage {
            trendbars = List.copyOf(trendbars);
        }
    }

    record CTraderSpotEvent(long symbolId, Long bid, Long ask, Instant occurredAt)
            implements CTraderStreamEvent { }
    record CTraderDepthQuote(long id, Long bid, Long ask, long size) { }
    record CTraderDepthEvent(long symbolId, List<CTraderDepthQuote> newQuotes,
                             List<Long> deletedQuoteIds) implements CTraderStreamEvent {
        public CTraderDepthEvent {
            newQuotes = List.copyOf(newQuotes == null ? List.of() : newQuotes);
            deletedQuoteIds = List.copyOf(deletedQuoteIds == null ? List.of() : deletedQuoteIds);
        }
    }
    record CTraderLiveTrendbarEvent(long symbolId, String period, CTraderTrendbar trendbar,
                                    Instant occurredAt, Long spotBid, boolean closeAvailable)
            implements CTraderStreamEvent {
        CTraderLiveTrendbarEvent(long symbolId, String period, CTraderTrendbar trendbar,
                                 Instant occurredAt) {
            this(symbolId, period, trendbar, occurredAt, null, true);
        }
    }

    sealed interface CTraderStreamEvent permits CTraderSpotEvent, CTraderDepthEvent, CTraderLiveTrendbarEvent { }

    interface CTraderStream extends AutoCloseable {
        void subscribeSpots(long symbolId);

        default void subscribeSpots(List<Long> symbolIds) {
            symbolIds.forEach(this::subscribeSpots);
        }

        void unsubscribeSpots(long symbolId);

        default void unsubscribeSpots(List<Long> symbolIds) {
            symbolIds.forEach(this::unsubscribeSpots);
        }

        default void subscribeDepth(long symbolId) {
            throw new UnsupportedOperationException("cTrader depth subscriptions are unavailable");
        }

        default void subscribeDepth(List<Long> symbolIds) {
            symbolIds.forEach(this::subscribeDepth);
        }

        default void unsubscribeDepth(long symbolId) {
            throw new UnsupportedOperationException("cTrader depth subscriptions are unavailable");
        }

        default void unsubscribeDepth(List<Long> symbolIds) {
            symbolIds.forEach(this::unsubscribeDepth);
        }

        void subscribeLiveTrendbar(long symbolId, String period);
        void unsubscribeLiveTrendbar(long symbolId, String period);
        @Override void close();
    }
}
