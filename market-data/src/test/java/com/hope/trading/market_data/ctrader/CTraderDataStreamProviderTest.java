package com.hope.trading.market_data.ctrader;

import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketStateBuilder;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketConstraints;
import com.hope.trading.market_data.model.MarketStreamParameters;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.service.OhlcEventPublisher;
import com.hope.trading.market_data.service.TickerEventPublisher;
import com.hope.trading.market_data.ctrader.authentication.CTraderEnvironment;
import com.hope.trading.market_data.ctrader.authentication.CTraderMarketDataProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class CTraderDataStreamProviderTest {
    private static final Instant RECEIVED = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void ignoresIncompleteQuotesUntilBothSidesAreAvailable() {
        Fixture fixture = new Fixture();
        MarketStreamRequest request = new MarketStreamRequest(MarketStreamType.TICKER, null);
        fixture.provider.subscribe(List.of(fixture.market), request).block();

        fixture.emit(new CTraderOpenApiClient.CTraderSpotEvent(42, 12345L, null,
                RECEIVED.minusSeconds(1)));
        fixture.emit(new CTraderOpenApiClient.CTraderSpotEvent(42, null, 12355L,
                RECEIVED));
        fixture.emit(new CTraderOpenApiClient.CTraderSpotEvent(42, 12345L, 12355L,
                RECEIVED));

        assertThat(fixture.tickers).hasSize(1);
        assertThat(fixture.tickers.getFirst().bid()).isEqualByComparingTo("12345");
        assertThat(fixture.tickers.getFirst().ask()).isEqualByComparingTo("12355");
        assertThat(fixture.tickers.getFirst().last()).isNull();
        assertThat(fixture.tickers.getFirst().fetchedAt()).isEqualTo(RECEIVED);
        assertThat(fixture.tickers.getFirst().providerSymbol()).isEqualTo("42");
    }

    @Test
    void publishesFormingBarsAndClosesThePreviousBarOnlyWhenNextProviderBarArrives() {
        Fixture fixture = new Fixture();
        MarketStreamRequest request = new MarketStreamRequest(MarketStreamType.OHLC,
                new MarketStreamParameters(1, null));
        fixture.provider.subscribe(List.of(fixture.market), request).block();

        fixture.emit(bar(600, 100, RECEIVED.minusSeconds(30)));
        fixture.emit(bar(600, 100, RECEIVED.minusSeconds(30)));
        fixture.emit(bar(660, 110, RECEIVED));
        fixture.emit(bar(600, 100, RECEIVED.minusSeconds(29)));

        assertThat(fixture.ohlc).hasSize(3);
        assertThat(fixture.ohlc.get(0).closed()).isFalse();
        assertThat(fixture.ohlc.get(1).closed()).isTrue();
        assertThat(fixture.ohlc.get(1).closeTime()).isEqualTo(Instant.ofEpochSecond(660));
        assertThat(fixture.ohlc.get(2).closed()).isFalse();
    }

    @Test
    void usesSpotBidAsFormingCloseWhenProviderOmitsDeltaClose() {
        Fixture fixture = new Fixture();
        MarketStreamRequest request = new MarketStreamRequest(MarketStreamType.OHLC,
                new MarketStreamParameters(1, null));
        fixture.provider.subscribe(List.of(fixture.market), request).block();

        fixture.emit(new CTraderOpenApiClient.CTraderLiveTrendbarEvent(42, "M1",
                new CTraderOpenApiClient.CTraderTrendbar(600, 1, 3, 100, 0, 100),
                RECEIVED, 12345L, false));

        assertThat(fixture.ohlc).hasSize(1);
        assertThat(fixture.ohlc.getFirst().closed()).isFalse();
        assertThat(fixture.ohlc.getFirst().close()).isEqualByComparingTo("12345");
    }

    @Test
    void deduplicatesRepeatedFormingUpdatesWithoutDeltaClose() {
        Fixture fixture = new Fixture();
        MarketStreamRequest request = new MarketStreamRequest(MarketStreamType.OHLC,
                new MarketStreamParameters(1, null));
        fixture.provider.subscribe(List.of(fixture.market), request).block();
        CTraderOpenApiClient.CTraderTrendbar bar =
                new CTraderOpenApiClient.CTraderTrendbar(600, 1, 3, 100, 0, 100);

        fixture.emit(new CTraderOpenApiClient.CTraderLiveTrendbarEvent(42, "M1", bar,
                RECEIVED, 12345L, false));
        fixture.emit(new CTraderOpenApiClient.CTraderLiveTrendbarEvent(42, "M1", bar,
                RECEIVED, 12345L, false));

        assertThat(fixture.ohlc).hasSize(1);
    }

    @Test
    void doesNotDuplicateSubscriptionsAndReplaysThemAfterTermination() throws Exception {
        Fixture fixture = new Fixture();
        MarketStreamRequest request = new MarketStreamRequest(MarketStreamType.TICKER, null);
        fixture.provider.subscribe(List.of(fixture.market), request).block();
        fixture.provider.subscribe(List.of(fixture.market), request).block();
        assertThat(fixture.client.streams.getFirst().spotSubscriptions).containsExactly(42L);

        fixture.terminate();
        Thread.sleep(1_200);
        assertThat(fixture.client.streams).hasSize(2);
        assertThat(fixture.client.streams.get(1).spotSubscriptions).containsExactly(42L);
    }

    @Test
    void stopsAutomaticReconnectAfterMaximumAttempts() throws Exception {
        Fixture fixture = new Fixture();
        MarketStreamRequest request = new MarketStreamRequest(MarketStreamType.TICKER, null);
        fixture.provider.subscribe(List.of(fixture.market), request).block();

        for (int attempt = 0; attempt <= 3; attempt++) {
            fixture.terminate();
            Thread.sleep(50);
        }

        assertThat(fixture.client.streams).hasSize(4);
    }

    @Test
    void rejectsUnsupportedAndInvalidProviderRequests() {
        Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.provider.subscribe(List.of(fixture.market),
                new MarketStreamRequest(MarketStreamType.TRADES, null)).block())
                .isInstanceOf(UnsupportedOperationException.class);
        Market invalid = Market.builder().marketId(UUID.randomUUID()).provider(MarketProvider.CTRADER)
                .symbol("EUR/USD").providerSymbol("not-a-symbol").baseAsset("EUR").quoteAsset("USD")
                .productType(InstrumentProductType.SPOT).marketConstraints(fixture.market.getMarketConstraints())
                .marketState(fixture.market.getMarketState()).build();
        assertThatThrownBy(() -> fixture.provider.subscribe(List.of(invalid),
                new MarketStreamRequest(MarketStreamType.TICKER, null)).block())
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CTraderOpenApiClient.CTraderLiveTrendbarEvent bar(long timestamp, long low, Instant occurredAt) {
        return new CTraderOpenApiClient.CTraderLiveTrendbarEvent(42, "M1",
                new CTraderOpenApiClient.CTraderTrendbar(timestamp, 1, 3, low, 2, 100), occurredAt);
    }

    private final class Fixture {
        private final List<TickerEvent> tickers = new ArrayList<>();
        private final List<OhlcEvent> ohlc = new ArrayList<>();
        private final Market market = Market.builder().marketId(UUID.randomUUID()).provider(MarketProvider.CTRADER)
                .symbol("EUR/USD").providerSymbol("42").baseAsset("EUR").quoteAsset("USD")
                .productType(InstrumentProductType.SPOT)
                .marketConstraints(MarketConstraints.builder().pricePrecision(0).build())
                .marketState(new MarketStateBuilder().buildInitialState(
                        com.hope.trading.market_data.helper.MarketAvailability.AVAILABLE)).build();
        private final FakeClient client = new FakeClient();
        private final TickerEventPublisher tickerPublisher = mock(TickerEventPublisher.class);
        private final OhlcEventPublisher ohlcPublisher = mock(OhlcEventPublisher.class);
        private final CTraderDataStreamProvider provider = new CTraderDataStreamProvider(client,
                tickerPublisher, ohlcPublisher, Clock.fixed(RECEIVED, ZoneOffset.UTC),
                new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "client", "secret", "wss://fixture",
                        java.time.Duration.ofSeconds(1), Map.of()), new MarketStateBuilder(),
                new java.time.Duration[]{java.time.Duration.ZERO, java.time.Duration.ZERO, java.time.Duration.ZERO});

        private Fixture() {
            doAnswer(invocation -> { tickers.add(invocation.getArgument(0)); return null; })
                    .when(tickerPublisher).publish(any(TickerEvent.class));
            doAnswer(invocation -> { ohlc.add(invocation.getArgument(0)); return null; })
                    .when(ohlcPublisher).publish(any(OhlcEvent.class));
        }
        private void emit(CTraderOpenApiClient.CTraderStreamEvent event) { client.events.accept(event); }
        private void terminate() { client.current.termination.run(); }
    }

    private static final class FakeClient implements CTraderOpenApiClient {
        private Consumer<CTraderStreamEvent> events;
        private FakeStream current;
        private final List<FakeStream> streams = new ArrayList<>();
        @Override public List<CTraderSymbol> symbols() { return List.of(); }
        @Override public CTraderSymbol symbol(long symbolId) { throw new UnsupportedOperationException(); }
        @Override public List<CTraderTrendbar> trendbars(long symbolId, String period, Instant from, Instant to, int count) { return List.of(); }
        @Override public CTraderStream openStream(Consumer<CTraderStreamEvent> events, Runnable termination) {
            this.events = events;
            current = new FakeStream(termination);
            streams.add(current);
            return current;
        }
    }

    private static final class FakeStream implements CTraderOpenApiClient.CTraderStream {
        private final Runnable termination;
        private final List<Long> spotSubscriptions = new ArrayList<>();
        private boolean closed;
        private FakeStream(Runnable termination) { this.termination = termination; }
        @Override public void subscribeSpots(long symbolId) { if (!spotSubscriptions.contains(symbolId)) spotSubscriptions.add(symbolId); }
        @Override public void unsubscribeSpots(long symbolId) { }
        @Override public void subscribeLiveTrendbar(long symbolId, String period) { }
        @Override public void unsubscribeLiveTrendbar(long symbolId, String period) { }
        @Override public void close() { closed = true; }
    }
}
