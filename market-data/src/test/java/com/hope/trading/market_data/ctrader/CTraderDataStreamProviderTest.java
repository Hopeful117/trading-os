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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

        fixture.emit(new CTraderOpenApiClient.CTraderSpotEvent(42, 123450L, null,
                RECEIVED.minusSeconds(1)));
        fixture.emit(new CTraderOpenApiClient.CTraderSpotEvent(42, null, 123550L,
                RECEIVED));
        fixture.emit(new CTraderOpenApiClient.CTraderSpotEvent(42, 123450L, 123550L,
                RECEIVED));

        assertThat(fixture.tickers).hasSize(1);
        assertThat(fixture.tickers.getFirst().bid()).isEqualByComparingTo("1.2345");
        assertThat(fixture.tickers.getFirst().ask()).isEqualByComparingTo("1.2355");
        assertThat(fixture.tickers.getFirst().last()).isNull();
        assertThat(fixture.tickers.getFirst().fetchedAt()).isEqualTo(RECEIVED);
        assertThat(fixture.tickers.getFirst().providerSymbol()).isEqualTo("42");
    }

    @Test
    void marksTheSubscribedMarketTradableWhenACompleteSpotArrives() {
        Fixture fixture = new Fixture();
        fixture.market.setMarketState(new MarketStateBuilder().buildInitialState(
                com.hope.trading.market_data.helper.MarketAvailability.UNAVAILABLE));
        fixture.provider.subscribe(List.of(fixture.market), new MarketStreamRequest(MarketStreamType.TICKER, null)).block();

        fixture.emit(new CTraderOpenApiClient.CTraderSpotEvent(42, 123450L, 123550L, RECEIVED));

        assertThat(fixture.market.getMarketState().isTradable()).isTrue();
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
                new CTraderOpenApiClient.CTraderTrendbar(600, 100, 300, 10000, 0, 100),
                RECEIVED, 123450L, false));

        assertThat(fixture.ohlc).hasSize(1);
        assertThat(fixture.ohlc.getFirst().closed()).isFalse();
        assertThat(fixture.ohlc.getFirst().close()).isEqualByComparingTo("1.2345");
    }

    @Test
    void deduplicatesRepeatedFormingUpdatesWithoutDeltaClose() {
        Fixture fixture = new Fixture();
        MarketStreamRequest request = new MarketStreamRequest(MarketStreamType.OHLC,
                new MarketStreamParameters(1, null));
        fixture.provider.subscribe(List.of(fixture.market), request).block();
        CTraderOpenApiClient.CTraderTrendbar bar =
                new CTraderOpenApiClient.CTraderTrendbar(600, 100, 300, 10000, 0, 100);

        fixture.emit(new CTraderOpenApiClient.CTraderLiveTrendbarEvent(42, "M1", bar,
                RECEIVED, 123450L, false));
        fixture.emit(new CTraderOpenApiClient.CTraderLiveTrendbarEvent(42, "M1", bar,
                RECEIVED, 123450L, false));

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
    void coordinatesConcurrentSpotBootstrapAcrossTickerAndOhlcDemand() throws Exception {
        Fixture fixture = new Fixture();
        fixture.client.spotStarted = new CountDownLatch(1);
        fixture.client.allowSpot = new CountDownLatch(1);

        CompletableFuture<Void> ticker = CompletableFuture.runAsync(() ->
                fixture.provider.subscribe(List.of(fixture.market),
                        new MarketStreamRequest(MarketStreamType.TICKER, null)).block());
        assertThat(fixture.client.spotStarted.await(2, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> ohlc = CompletableFuture.runAsync(() ->
                fixture.provider.subscribe(List.of(fixture.market),
                        new MarketStreamRequest(MarketStreamType.OHLC,
                                new MarketStreamParameters(1, null))).block());
        fixture.client.allowSpot.countDown();

        ticker.join();
        ohlc.join();

        assertThat(fixture.client.current.spotSubscriptionBatches).hasSize(1);
    }

    @Test
    void failedSpotBootstrapCanBeRetried() {
        Fixture fixture = new Fixture();
        fixture.client.failSpotSubscription = true;

        assertThatThrownBy(() -> fixture.provider.subscribe(List.of(fixture.market),
                new MarketStreamRequest(MarketStreamType.TICKER, null)).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("spot subscription failed");

        fixture.client.failSpotSubscription = false;
        fixture.provider.subscribe(List.of(fixture.market),
                new MarketStreamRequest(MarketStreamType.TICKER, null)).block();

        assertThat(fixture.client.current.spotSubscriptionBatches).hasSize(2);
    }

    @Test
    void failedOhlcSetupPreservesPreExistingSharedSpotSubscription() {
        Fixture fixture = new Fixture();
        MarketStreamRequest ticker = new MarketStreamRequest(MarketStreamType.TICKER, null);
        MarketStreamRequest ohlc = new MarketStreamRequest(MarketStreamType.OHLC,
                new MarketStreamParameters(1, null));
        fixture.provider.subscribe(List.of(fixture.market), ticker).block();
        fixture.client.failTrendbarSubscription = true;

        assertThatThrownBy(() -> fixture.provider.subscribe(List.of(fixture.market), ohlc).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("trendbar subscription failed");

        fixture.provider.subscribe(List.of(fixture.market), ticker).block();

        assertThat(fixture.client.current.spotSubscriptionBatches).hasSize(1);
        assertThat(fixture.client.current.unsubscribedSpots).isEmpty();
    }

    @Test
    void failedOhlcSetupRestartsSessionForOperationOwnedSpotAndAllowsRetry() {
        Fixture fixture = new Fixture();
        MarketStreamRequest ohlc = new MarketStreamRequest(MarketStreamType.OHLC,
                new MarketStreamParameters(1, null));
        fixture.client.failTrendbarSubscription = true;

        assertThatThrownBy(() -> fixture.provider.subscribe(List.of(fixture.market), ohlc).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("trendbar subscription failed");

        fixture.client.failTrendbarSubscription = false;
        fixture.provider.subscribe(List.of(fixture.market), ohlc).block();

        assertThat(fixture.client.streams).hasSize(2);
        assertThat(fixture.client.streams.get(0).closed).isTrue();
        assertThat(fixture.client.streams.get(1).spotSubscriptionBatches).containsExactly(List.of(42L));
        assertThat(fixture.client.streams.get(1).trendbarSubscriptions).containsExactly("42:M1");
    }

    @Test
    void batchesSpotSubscriptionsForMultipleMarkets() {
        Fixture fixture = new Fixture();
        Market secondMarket = Market.builder().marketId(UUID.randomUUID()).provider(MarketProvider.CTRADER)
                .symbol("GBP/USD").providerSymbol("43").baseAsset("GBP").quoteAsset("USD")
                .productType(InstrumentProductType.SPOT)
                .marketConstraints(fixture.market.getMarketConstraints())
                .marketState(fixture.market.getMarketState()).build();

        fixture.provider.subscribe(List.of(fixture.market, secondMarket),
                new MarketStreamRequest(MarketStreamType.TICKER, null)).block();

        assertThat(fixture.client.streams.getFirst().spotSubscriptionBatches)
                .containsExactly(List.of(42L, 43L));
    }

    @Test
    void unsubscribesFinalSpotDemandWithoutClosingTheSharedStream() {
        Fixture fixture = new Fixture();
        MarketStreamRequest request = new MarketStreamRequest(MarketStreamType.TICKER, null);
        fixture.provider.subscribe(List.of(fixture.market), request).block();

        fixture.provider.unsubscribe(List.of(fixture.market), request).block();

        assertThat(fixture.client.streams).hasSize(1);
        assertThat(fixture.client.current.closed).isFalse();
        assertThat(fixture.client.current.unsubscribedSpots).containsExactly(42L);
    }

    @Test
    void preservesRemainingSpotDemandWhenOneMarketIsReleased() {
        Fixture fixture = new Fixture();
        Market second = Market.builder().marketId(UUID.randomUUID()).provider(MarketProvider.CTRADER)
                .symbol("GBP/USD").providerSymbol("43").baseAsset("GBP").quoteAsset("USD")
                .productType(InstrumentProductType.SPOT)
                .marketConstraints(fixture.market.getMarketConstraints())
                .marketState(fixture.market.getMarketState()).build();
        MarketStreamRequest request = new MarketStreamRequest(MarketStreamType.TICKER, null);
        fixture.provider.subscribe(List.of(fixture.market, second), request).block();

        fixture.provider.unsubscribe(List.of(fixture.market), request).block();

        assertThat(fixture.client.streams).hasSize(1);
        assertThat(fixture.client.current.unsubscribedSpots).containsExactly(42L);
        assertThat(fixture.client.current.spotSubscriptions).containsExactly(42L, 43L);
    }

    @Test
    void preservesSharedSpotDemandUntilTheLastTickerOrOhlcConsumerReleases() {
        Fixture fixture = new Fixture();
        MarketStreamRequest ticker = new MarketStreamRequest(MarketStreamType.TICKER, null);
        MarketStreamRequest ohlc = new MarketStreamRequest(MarketStreamType.OHLC,
                new MarketStreamParameters(1, null));
        fixture.provider.subscribe(List.of(fixture.market), ticker).block();
        fixture.provider.subscribe(List.of(fixture.market), ohlc).block();

        fixture.provider.unsubscribe(List.of(fixture.market), ticker).block();
        assertThat(fixture.client.current.unsubscribedSpots).isEmpty();

        fixture.provider.unsubscribe(List.of(fixture.market), ohlc).block();
        assertThat(fixture.client.current.unsubscribedSpots).containsExactly(42L);
    }

    @Test
    void unsubscribesOnlyTheReleasedTrendbarInterval() {
        Fixture fixture = new Fixture();
        MarketStreamRequest m1 = new MarketStreamRequest(MarketStreamType.OHLC,
                new MarketStreamParameters(1, null));
        MarketStreamRequest m5 = new MarketStreamRequest(MarketStreamType.OHLC,
                new MarketStreamParameters(5, null));
        fixture.provider.subscribe(List.of(fixture.market), m1).block();
        fixture.provider.subscribe(List.of(fixture.market), m5).block();

        fixture.provider.unsubscribe(List.of(fixture.market), m1).block();

        assertThat(fixture.client.current.unsubscribedTrendbars).containsExactly("42:M1");
        assertThat(fixture.client.current.unsubscribedSpots).isEmpty();
        assertThat(fixture.client.streams).hasSize(1);
    }

    @Test
    void failedUnsubscribeReconcilesTheRemainingDesiredSetOnANewSession() {
        Fixture fixture = new Fixture();
        Market second = Market.builder().marketId(UUID.randomUUID()).provider(MarketProvider.CTRADER)
                .symbol("GBP/USD").providerSymbol("43").baseAsset("GBP").quoteAsset("USD")
                .productType(InstrumentProductType.SPOT)
                .marketConstraints(fixture.market.getMarketConstraints())
                .marketState(fixture.market.getMarketState()).build();
        MarketStreamRequest request = new MarketStreamRequest(MarketStreamType.TICKER, null);
        fixture.provider.subscribe(List.of(fixture.market, second), request).block();
        fixture.client.failSpotUnsubscription = true;

        assertThatThrownBy(() -> fixture.provider.unsubscribe(List.of(fixture.market), request).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("spot unsubscription failed");

        assertThat(fixture.client.streams).hasSize(2);
        assertThat(fixture.client.streams.getFirst().closed).isTrue();
        assertThat(fixture.client.streams.get(1).spotSubscriptionBatches).containsExactly(List.of(43L));
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
                .marketConstraints(MarketConstraints.builder().pricePrecision(5).build())
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
        private CountDownLatch spotStarted;
        private CountDownLatch allowSpot;
        private boolean failSpotSubscription;
        private boolean failSpotUnsubscription;
        private boolean failTrendbarSubscription;
        @Override public List<CTraderSymbol> symbols() { return List.of(); }
        @Override public CTraderSymbol symbol(long symbolId) { throw new UnsupportedOperationException(); }
        @Override public List<CTraderTrendbar> trendbars(long symbolId, String period, Instant from, Instant to, int count) { return List.of(); }
        @Override public CTraderStream openStream(Consumer<CTraderStreamEvent> events, Runnable termination) {
            this.events = events;
            current = new FakeStream(this, termination);
            streams.add(current);
            return current;
        }
    }

    private static final class FakeStream implements CTraderOpenApiClient.CTraderStream {
        private final FakeClient client;
        private final Runnable termination;
        private final List<Long> spotSubscriptions = new ArrayList<>();
        private final List<List<Long>> spotSubscriptionBatches = new ArrayList<>();
        private final List<Long> unsubscribedSpots = new ArrayList<>();
        private final List<String> trendbarSubscriptions = new ArrayList<>();
        private final List<String> unsubscribedTrendbars = new ArrayList<>();
        private boolean closed;
        private FakeStream(FakeClient client, Runnable termination) {
            this.client = client;
            this.termination = termination;
        }
        @Override public void subscribeSpots(long symbolId) { if (!spotSubscriptions.contains(symbolId)) spotSubscriptions.add(symbolId); }
        @Override public void subscribeSpots(List<Long> symbolIds) {
            spotSubscriptionBatches.add(List.copyOf(symbolIds));
            if (client.spotStarted != null) client.spotStarted.countDown();
            if (client.allowSpot != null) {
                try {
                    client.allowSpot.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("spot subscription interrupted", exception);
                }
            }
            if (client.failSpotSubscription) throw new IllegalStateException("spot subscription failed");
            symbolIds.forEach(this::subscribeSpots);
        }
        @Override public void unsubscribeSpots(long symbolId) {
            if (client.failSpotUnsubscription) throw new IllegalStateException("spot unsubscription failed");
            unsubscribedSpots.add(symbolId);
        }
        @Override public void subscribeLiveTrendbar(long symbolId, String period) {
            if (client.failTrendbarSubscription) throw new IllegalStateException("trendbar subscription failed");
            trendbarSubscriptions.add(symbolId + ":" + period);
        }
        @Override public void unsubscribeLiveTrendbar(long symbolId, String period) {
            unsubscribedTrendbars.add(symbolId + ":" + period);
        }
        @Override public void close() { closed = true; }
    }
}
