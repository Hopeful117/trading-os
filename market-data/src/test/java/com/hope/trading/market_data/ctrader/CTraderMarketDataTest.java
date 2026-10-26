package com.hope.trading.market_data.ctrader;

import com.hope.trading.market_data.ctrader.authentication.CTraderEnvironment;
import com.hope.trading.market_data.ctrader.authentication.CTraderMarketDataProperties;
import com.hope.trading.market_data.helper.MarketStateBuilder;
import com.hope.trading.market_data.model.OhlcInterval;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                return List.of(new CTraderTrendbar(NOW.minusSeconds(30).getEpochSecond(), 1, 3, 12345, 2, 100));
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
    void rejectsUnmappedSymbolsInsteadOfGuessingCanonicalIdentity() {
        CTraderMarketData provider = new CTraderMarketData(new FakeClient(), propertiesWithNoMappings(),
                new MarketStateBuilder(), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(provider.getMarkets()).isEmpty();
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
                new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "id", "secret", "wss://fixture",
                        java.time.Duration.ofSeconds(1), Map.of("EURUSD", "EUR/USD", "EURUSD.pro", "EUR/USD")),
                new MarketStateBuilder(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(provider::getMarkets)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous cTrader mapping");
    }

    private CTraderMarketDataProperties properties() {
        return new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "id", "secret", "wss://fixture",
                java.time.Duration.ofSeconds(1), Map.of("EURUSD", "EUR/USD"));
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
            return List.of(new CTraderTrendbar(NOW.minusSeconds(60).getEpochSecond(), 1, 3, 12345, 2, 100));
        }
    }
}
