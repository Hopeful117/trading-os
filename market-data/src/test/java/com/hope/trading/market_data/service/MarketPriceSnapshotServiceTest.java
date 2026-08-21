package com.hope.trading.market_data.service;

import com.hope.trading.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketState;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.repository.MarketRepository;
import com.hope.trading.market_data.repository.PriceObservationRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketPriceSnapshotServiceTest {
    private final MarketRepository repository = mock(MarketRepository.class);
    private final TickerEventPublisher publisher = new TickerEventPublisher(
            repository, mock(PriceObservationRepository.class), Clock.systemUTC());
    private final StubProvider provider = new StubProvider();
    private final Clock clock =
            Clock.fixed(Instant.parse("2026-08-01T12:00:01Z"), java.time.ZoneOffset.UTC);
    private final MarketPriceSnapshotService service =
            new MarketPriceSnapshotService(
                    repository,
                    publisher,
                    List.of(provider),
                    clock,
                    Duration.ofSeconds(30));

    @Test
    void returnsAvailableMissingAndUnknownPricesInRequestedOrder() {
        UUID availableId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        Market available = market(availableId, "BTC/USD", true);
        Market missing = market(missingId, "ETH/USD", false);
        when(repository.findAllById(List.of(availableId, missingId, unknownId)))
                .thenReturn(List.of(available, missing));
        publisher.publish(new TickerEvent(
                availableId, MarketProvider.KRAKEN, "BTC/USD",
                new BigDecimal("99"), new BigDecimal("101"),
                new BigDecimal("100"), BigDecimal.ONE, Instant.now()
        ));

        var result = service.findSnapshots(List.of(availableId, missingId, unknownId));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).status()).isEqualTo(MarketPriceSnapshotStatus.FRESH);
        assertThat(result.get(0).lastPrice()).isEqualByComparingTo("100");
        assertThat(result.get(0).sourceSnapshotId()).startsWith("ticker:");
        assertThat(result.get(0).sourceSnapshotVersion()).isPositive();
        assertThat(result.get(0).capturedAt()).isEqualTo(result.get(0).occurredAt());
        assertThat(result.get(1).status()).isEqualTo(MarketPriceSnapshotStatus.UNAVAILABLE);
        assertThat(result.get(1).tradable()).isFalse();
        assertThat(result.get(2).status()).isEqualTo(MarketPriceSnapshotStatus.UNKNOWN_MARKET);
    }

    @Test
    void sourceIdentityIsStableForTheSameTickerFact() {
        UUID marketId = UUID.randomUUID();
        Market market = market(marketId, "BTC/USD", true);
        when(repository.findAllById(List.of(marketId))).thenReturn(List.of(market));
        publisher.publish(new TickerEvent(
                marketId, MarketProvider.KRAKEN, "BTC/USD",
                new BigDecimal("99"), new BigDecimal("101"),
                new BigDecimal("100"), BigDecimal.ONE,
                Instant.parse("2026-08-01T12:00:00Z")));

        var first = service.findSnapshots(List.of(marketId)).getFirst();
        var second = service.findSnapshots(List.of(marketId)).getFirst();

        assertThat(second.sourceSnapshotId()).isEqualTo(first.sourceSnapshotId());
        assertThat(second.sourceSnapshotVersion()).isEqualTo(first.sourceSnapshotVersion());
    }

    @Test
    void deDuplicatesRequestedMarketIds() {
        UUID marketId = UUID.randomUUID();
        when(repository.findAllById(List.of(marketId))).thenReturn(List.of());

        assertThat(service.findSnapshots(List.of(marketId, marketId))).hasSize(1);
    }

    @Test
    void missingCacheUsesProviderAndPopulatesUnifiedCurrentStateCache() {
        UUID marketId = UUID.randomUUID();
        Market market = market(marketId, "ETH/USD", true);
        when(repository.findAllById(List.of(marketId))).thenReturn(List.of(market));
        provider.next = Optional.of(ticker(marketId, "ETH/USD", "199", "201", "200", "2026-08-01T12:00:00Z"));

        var first = service.findSnapshots(List.of(marketId)).getFirst();
        var second = service.findSnapshots(List.of(marketId)).getFirst();

        assertThat(first.status()).isEqualTo(MarketPriceSnapshotStatus.FRESH);
        assertThat(second.status()).isEqualTo(MarketPriceSnapshotStatus.FRESH);
        assertThat(provider.calls).hasValue(1);
        assertThat(second.bid()).isEqualByComparingTo("199");
        assertThat(second.ask()).isEqualByComparingTo("201");
    }

    @Test
    void staleCacheRefreshesThroughProvider() {
        UUID marketId = UUID.randomUUID();
        Market market = market(marketId, "ETH/USD", true);
        when(repository.findAllById(List.of(marketId))).thenReturn(List.of(market));
        publisher.publish(ticker(marketId, "ETH/USD", "100", "102", "101", "2026-08-01T11:59:00Z"));
        provider.next = Optional.of(ticker(marketId, "ETH/USD", "209", "211", "210", "2026-08-01T12:00:00Z"));

        var result = service.findSnapshots(List.of(marketId)).getFirst();

        assertThat(result.status()).isEqualTo(MarketPriceSnapshotStatus.FRESH);
        assertThat(result.bid()).isEqualByComparingTo("209");
        assertThat(result.ask()).isEqualByComparingTo("211");
        assertThat(provider.calls).hasValue(1);
    }

    @Test
    void staleCacheReturnsStaleWhenRefreshFails() {
        UUID marketId = UUID.randomUUID();
        Market market = market(marketId, "ETH/USD", true);
        when(repository.findAllById(List.of(marketId))).thenReturn(List.of(market));
        publisher.publish(ticker(marketId, "ETH/USD", "100", "102", "101", "2026-08-01T11:59:00Z"));
        provider.failure = new IllegalStateException("Provider unavailable");

        var result = service.findSnapshots(List.of(marketId)).getFirst();

        assertThat(result.status()).isEqualTo(MarketPriceSnapshotStatus.STALE);
        assertThat(result.bid()).isEqualByComparingTo("100");
        assertThat(result.ask()).isEqualByComparingTo("102");
    }

    @Test
    void missingCacheReturnsUnavailableWhenProviderFails() {
        UUID marketId = UUID.randomUUID();
        Market market = market(marketId, "ETH/USD", true);
        when(repository.findAllById(List.of(marketId))).thenReturn(List.of(market));
        provider.failure = new IllegalStateException("Provider unavailable");

        var result = service.findSnapshots(List.of(marketId)).getFirst();

        assertThat(result.status()).isEqualTo(MarketPriceSnapshotStatus.UNAVAILABLE);
        assertThat(result.bid()).isNull();
        assertThat(result.ask()).isNull();
    }

    @Test
    void concurrentColdRequestsForSameMarketTriggerSingleProviderAcquisition() throws Exception {
        UUID marketId = UUID.randomUUID();
        Market market = market(marketId, "ETH/USD", true);
        when(repository.findAllById(List.of(marketId))).thenReturn(List.of(market));
        provider.next = Optional.of(ticker(marketId, "ETH/USD", "299", "301", "300", "2026-08-01T12:00:00Z"));
        provider.blocking = true;

        int callers = 5;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch started = new CountDownLatch(callers);
        CountDownLatch release = new CountDownLatch(1);
        provider.started = started;
        provider.release = release;
        try {
            var futures = java.util.stream.IntStream.range(0, callers)
                    .mapToObj(ignored -> executor.submit(() -> {
                        started.countDown();
                        return service.findSnapshots(List.of(marketId)).getFirst();
                    }))
                    .toList();
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            for (Future<?> future : futures) {
                assertThat(((com.hope.trading.market_data.dto.MarketPriceSnapshot) future.get()).status())
                        .isEqualTo(MarketPriceSnapshotStatus.FRESH);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(provider.calls).hasValue(1);
    }

    @Test
    void differentMarketsAcquireIndependently() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Market first = market(firstId, "ETH/USD", true);
        Market second = market(secondId, "XBT/EUR", true);
        when(repository.findAllById(List.of(firstId, secondId))).thenReturn(List.of(first, second));
        provider.responses.put(firstId, Optional.of(ticker(firstId, "ETH/USD", "10", "11", "10.5", "2026-08-01T12:00:00Z")));
        provider.responses.put(secondId, Optional.of(ticker(secondId, "XBT/EUR", "20", "21", "20.5", "2026-08-01T12:00:00Z")));

        var result = service.findSnapshots(List.of(firstId, secondId));

        assertThat(result).extracting(item -> item.status())
                .containsExactly(MarketPriceSnapshotStatus.FRESH, MarketPriceSnapshotStatus.FRESH);
        assertThat(provider.calls).hasValue(2);
    }

    private Market market(UUID id, String symbol, boolean tradable) {
        return Market.builder()
                .marketId(id)
                .provider(MarketProvider.KRAKEN)
                .symbol(symbol)
                .marketState(MarketState.builder().tradable(tradable).build())
                .build();
    }

    private TickerEvent ticker(
            UUID marketId,
            String symbol,
            String bid,
            String ask,
            String last,
            String occurredAt
    ) {
        return new TickerEvent(
                marketId,
                MarketProvider.KRAKEN,
                symbol,
                new BigDecimal(bid),
                new BigDecimal(ask),
                new BigDecimal(last),
                BigDecimal.ONE,
                Instant.parse(occurredAt)
        );
    }

    private static final class StubProvider implements MarketDataProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private final ConcurrentMap<UUID, Optional<TickerEvent>> responses = new ConcurrentHashMap<>();
        private volatile Optional<TickerEvent> next = Optional.empty();
        private volatile RuntimeException failure;
        private volatile boolean blocking;
        private volatile CountDownLatch started;
        private volatile CountDownLatch release;

        @Override
        public List<Market> getMarkets() {
            return List.of();
        }

        @Override
        public MarketProvider getName() {
            return MarketProvider.KRAKEN;
        }

        @Override
        public Optional<TickerEvent> acquireCurrentSnapshot(Market market) {
            calls.incrementAndGet();
            if (blocking && release != null) {
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted", exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
            return responses.getOrDefault(market.getMarketId(), next);
        }

        @Override
        public List<OhlcEvent> findOhlcHistory(Market market, OhlcInterval interval, int limit) {
            return List.of();
        }
    }
}
