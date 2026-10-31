package com.hope.trading.market_data.ctrader;

import com.hope.trading.market_data.ctrader.authentication.CTraderEnvironment;
import com.hope.trading.market_data.ctrader.authentication.CTraderMarketDataProperties;
import com.hope.trading.market_data.helper.MarketStateBuilder;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.MarketActivityCalculationBasis;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CTraderMarketDataTest {
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void mapsExplicitSymbolAndScalesRelativeTrendbarPrices() {
        FakeClient client = new FakeClient();
        CTraderMarketData provider = new CTraderMarketData(client, properties(), new MarketStateBuilder(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var market = provider.getMarkets().getFirst();
        market.setMarketId(UUID.randomUUID());
        var events = provider.findOhlcHistory(market, OhlcInterval.ONE_MINUTE, 1);

        assertThat(market.getSymbol()).isEqualTo("EUR/USD");
        assertThat(market.getProductType())
                .isEqualTo(com.hope.trading.market_data.helper.InstrumentProductType.CFD);
        assertThat(market.getMarketState().isTradable()).isFalse();
        assertThat(market.getMarketState().getTradingStatus()).isEqualTo(
                com.hope.trading.market_data.helper.TradingStatus.CLOSED);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.open()).isEqualByComparingTo("1.2346");
            assertThat(event.high()).isEqualByComparingTo("1.2348");
            assertThat(event.low()).isEqualByComparingTo("1.2345");
            assertThat(event.close()).isEqualByComparingTo("1.2347");
            assertThat(event.closed()).isTrue();
            assertThat(event.providerSymbol()).isEqualTo("42");
        });
    }

    @Test
    void keepsCurrentBarObservedButExcludesItFromNormalizedHistory() {
        FakeClient client = new FakeClient() {
            @Override public List<CTraderTrendbar> trendbars(long symbolId, String period, Instant from, Instant to, int count) {
                return List.of(new CTraderTrendbar(NOW.minusSeconds(30).getEpochSecond(), 100, 300, 123450, 200, 100));
            }
        };
        CTraderMarketData provider = new CTraderMarketData(client, properties(), new MarketStateBuilder(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var market = provider.getMarkets().getFirst();
        market.setMarketId(UUID.randomUUID());
        var snapshot = provider.findOhlcHistorySnapshot(market, OhlcInterval.ONE_MINUTE, 1);
        assertThat(snapshot.observedEvents()).hasSize(1);
        assertThat(snapshot.normalizedEvents()).isEmpty();
    }

    @Test
    void expandsCalendarLookbackWhenProviderReturnsFewerClosedBarsThanRequested() {
        AtomicInteger calls = new AtomicInteger();
        FakeClient client = new FakeClient() {
            @Override public List<CTraderTrendbar> trendbars(
                    long symbolId, String period, Instant from, Instant to, int count) {
                if (calls.incrementAndGet() == 1) {
                    return List.of(closedBar(NOW.minusSeconds(180)), closedBar(NOW.minusSeconds(60)));
                }
                return List.of(closedBar(NOW.minusSeconds(180)), closedBar(NOW.minusSeconds(120)),
                        closedBar(NOW.minusSeconds(60)));
            }

            private CTraderTrendbar closedBar(Instant open) {
                return new CTraderTrendbar(open.getEpochSecond(), 10, 30, 123450, 20, 100);
            }
        };
        CTraderMarketData provider = new CTraderMarketData(client, properties(), new MarketStateBuilder(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var market = provider.getMarkets().getFirst();
        market.setMarketId(UUID.randomUUID());

        var snapshot = provider.findOhlcHistorySnapshot(market, OhlcInterval.ONE_MINUTE, 3);

        assertThat(snapshot.normalizedEvents()).hasSize(3);
        assertThat(snapshot.normalizedEvents()).extracting(OhlcEvent::openTime)
                .isSorted();
        assertThat(calls).hasValue(2);
    }

    @Test
    void remainsFailClosedWhenBoundedLookbackCannotSupplyRequiredHistory() {
        CTraderMarketData provider = new CTraderMarketData(new FakeClient() {
            @Override public List<CTraderTrendbar> trendbars(
                    long symbolId, String period, Instant from, Instant to, int count) {
                return List.of(new CTraderTrendbar(NOW.minusSeconds(180).getEpochSecond(),
                        10, 30, 123450, 20, 100),
                        new CTraderTrendbar(NOW.minusSeconds(60).getEpochSecond(),
                                10, 30, 123450, 20, 100));
            }
        }, properties(), new MarketStateBuilder(), Clock.fixed(NOW, ZoneOffset.UTC));
        var market = provider.getMarkets().getFirst();
        market.setMarketId(UUID.randomUUID());

        var snapshot = provider.findOhlcHistorySnapshot(market, OhlcInterval.ONE_MINUTE, 3);

        assertThat(snapshot.normalizedEvents()).hasSize(2);
    }

    @Test
    void declaresCloseFallbackWhenCTraderHistoryHasNoVwap() {
        CTraderMarketData provider = new CTraderMarketData(new FakeClient(), properties(), new MarketStateBuilder(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var market = provider.getMarkets().getFirst();
        market.setMarketId(UUID.randomUUID());

        var snapshot = provider.findOhlcHistorySnapshot(market, OhlcInterval.ONE_MINUTE, 1);

        assertThat(snapshot.activityCalculationBasis())
                .isEqualTo(MarketActivityCalculationBasis.CLOSE_PRICE_FALLBACK);
        assertThat(snapshot.observedEvents()).singleElement()
                .satisfies(event -> assertThat(event.vwap()).isNull());
    }

    @Test
    void rejectsUnmappedSymbolsInsteadOfGuessingCanonicalIdentity() {
        CTraderMarketData provider = new CTraderMarketData(new FakeClient(), propertiesWithNoMappings(),
                new MarketStateBuilder(), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(provider.getMarkets()).isEmpty();
    }

    @Test
    void rejectsSymbolsWithoutExplicitProductTypeInsteadOfDefaultingToSpot() {
        CTraderMarketData provider = new CTraderMarketData(new FakeClient(),
                propertiesWithCanonicalMappingWithoutProductType(), new MarketStateBuilder(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(provider.getMarkets()).isEmpty();
    }

    @Test
    void rejectsSymbolsWithBlankProductTypeInsteadOfDefaultingToSpot() {
        CTraderMarketData provider = new CTraderMarketData(new FakeClient(),
                propertiesWithProductType(" "), new MarketStateBuilder(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(provider.getMarkets()).isEmpty();
    }

    @Test
    void mapsExplicitCfdProductType() {
        CTraderMarketData provider = new CTraderMarketData(new FakeClient(),
                propertiesWithProductType("CFD"), new MarketStateBuilder(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(provider.getMarkets()).singleElement()
                .extracting(com.hope.trading.market_data.model.Market::getProductType)
                .isEqualTo(com.hope.trading.market_data.helper.InstrumentProductType.CFD);
    }

    @Test
    void mapsEachConfiguredFallbackSymbolOnlyFromItsExplicitProductType() {
        CTraderOpenApiClient client = new FakeClient() {
            @Override public List<CTraderSymbol> symbols() {
                return List.of(new CTraderSymbol(42, "EURUSD", 4, 100, 1_000_000, 100),
                        new CTraderSymbol(43, "GBPUSD", 4, 100, 1_000_000, 100),
                        new CTraderSymbol(44, "XAUUSD", 2, 100, 1_000_000, 100),
                        new CTraderSymbol(45, "US30.cash", 1, 100, 1_000_000, 100));
            }
        };
        CTraderMarketData provider = new CTraderMarketData(client,
                new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "id", "secret", null, null, null,
                        "wss://fixture", java.time.Duration.ofSeconds(1), java.time.Duration.ofMinutes(10),
                        Map.of("EURUSD", "EUR/USD", "GBPUSD", "GBP/USD", "XAUUSD", "XAU/USD",
                                "US30.cash", "US30/USD"),
                        Map.of("EURUSD", "CFD", "GBPUSD", "CFD", "XAUUSD", "CFD", "US30.cash", "CFD"),
                        8, 720),
                new MarketStateBuilder(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(provider.getMarkets()).hasSize(4)
                .extracting(com.hope.trading.market_data.model.Market::getProductType)
                .containsOnly(com.hope.trading.market_data.helper.InstrumentProductType.CFD);
    }

    @Test
    void rejectsInvalidProductTypeWithProviderSymbol() {
        CTraderMarketData provider = new CTraderMarketData(new FakeClient(),
                propertiesWithProductType("UNKNOWN"), new MarketStateBuilder(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(provider::getMarkets)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EURUSD");
    }

    @Test
    void rejectsAmbiguousCanonicalMappings() {
        CTraderOpenApiClient client = new FakeClient() {
            @Override public List<CTraderSymbol> symbols() {
                return List.of(new CTraderSymbol(42, "EURUSD", 4, 100, 1_000_000, 100),
                        new CTraderSymbol(43, "EURUSD.pro", 4, 100, 1_000_000, 100));
            }
        };
        CTraderMarketData provider = new CTraderMarketData(client,
                new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "id", "secret", null, null, null,
                        "wss://fixture", java.time.Duration.ofSeconds(1), java.time.Duration.ofMinutes(10),
                        Map.of("EURUSD", "EUR/USD", "EURUSD.pro", "EUR/USD"),
                        Map.of("EURUSD", "SPOT", "EURUSD.pro", "SPOT"), 8, 720),
                new MarketStateBuilder(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(provider::getMarkets)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous cTrader mapping");
    }

    private CTraderMarketDataProperties properties() {
        return propertiesWithProductType("CFD");
    }

    private CTraderMarketDataProperties propertiesWithProductType(String productType) {
        return new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "id", "secret", null, null, null,
                "wss://fixture", java.time.Duration.ofSeconds(1), java.time.Duration.ofMinutes(10),
                Map.of("EURUSD", "EUR/USD"), Map.of("EURUSD", productType), 8, 720);
    }

    private CTraderMarketDataProperties propertiesWithCanonicalMappingWithoutProductType() {
        return new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "id", "secret", null, null, null,
                "wss://fixture", java.time.Duration.ofSeconds(1), java.time.Duration.ofMinutes(10),
                Map.of("EURUSD", "EUR/USD"), Map.of(), 8, 720);
    }

    private CTraderMarketDataProperties propertiesWithNoMappings() {
        return new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "id", "secret", "wss://fixture",
                java.time.Duration.ofSeconds(1), Map.of());
    }

    private static class FakeClient implements CTraderOpenApiClient {
        private final CTraderSymbol symbol = new CTraderSymbol(42, "EURUSD", 4, 100, 1_000_000, 100);
        @Override public List<CTraderSymbol> symbols() { return List.of(symbol); }
        @Override public CTraderSymbol symbol(long symbolId) { return symbol; }
        @Override public List<CTraderTrendbar> trendbars(long symbolId, String period, Instant from, Instant to, int count) {
            return List.of(new CTraderTrendbar(NOW.minusSeconds(60).getEpochSecond(), 10, 30, 123450, 20, 100));
        }
    }
}
