package com.hope.trading.market_data.ctrader.e2e;

import com.hope.trading.market_data.ctrader.CTraderOpenApiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Primary
@Component
@ConditionalOnProperty(name = "e2e.controlled-ctrader-market-data.enabled", havingValue = "true")
public class ControlledCTraderOpenApiClient implements CTraderOpenApiClient {
    private static final long EURUSD_SYMBOL_ID = 41L;
    private static final int PRICE_DIGITS = 5;

    @Override
    public List<CTraderSymbol> symbols() {
        return List.of(new CTraderSymbol(
                EURUSD_SYMBOL_ID,
                "EURUSD",
                PRICE_DIGITS,
                1_000,
                100_000_000,
                1_000,
                "ENABLED",
                "UTC",
                List.of(new CTraderInterval(0, 604_799)),
                List.of()
        ));
    }

    @Override
    public CTraderSymbol symbol(long symbolId) {
        return symbols().stream()
                .filter(symbol -> symbol.id() == symbolId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown controlled cTrader symbol: " + symbolId));
    }

    @Override
    public Optional<CTraderSpotEvent> currentSpot(long symbolId) {
        Instant now = Instant.now();
        return Optional.of(new CTraderSpotEvent(symbolId, 1_085_000L, 1_085_200L, now));
    }

    @Override
    public List<CTraderTrendbar> trendbars(long symbolId, String period, Instant from, Instant to, int count) {
        Instant now = Instant.now();
        long minute = 60L;
        return List.of(
                new CTraderTrendbar(now.minusSeconds(2 * minute).toEpochMilli(), 1_084_000L, 1_085_500L,
                        1_083_500L, 1_085_000L, 100L),
                new CTraderTrendbar(now.minusSeconds(minute).toEpochMilli(), 1_085_000L, 1_086_000L,
                        1_084_500L, 1_085_200L, 120L)
        );
    }

    @Override
    public CTraderStream openStream(Consumer<CTraderStreamEvent> events, Runnable termination) {
        return new CTraderStream() {
            @Override
            public void subscribeSpots(long symbolId) {
                events.accept(currentSpot(symbolId).orElseThrow());
            }

            @Override
            public void unsubscribeSpots(long symbolId) { }

            @Override
            public void subscribeLiveTrendbar(long symbolId, String period) { }

            @Override
            public void unsubscribeLiveTrendbar(long symbolId, String period) { }

            @Override
            public void close() { }
        };
    }
}
