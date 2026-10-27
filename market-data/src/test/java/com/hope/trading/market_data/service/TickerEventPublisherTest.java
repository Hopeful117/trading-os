package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.repository.MarketRepository;
import com.hope.trading.market_data.repository.PriceObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-3B: protects the live ticker fan-out contract — latest-state
 * snapshot for late subscribers, symbol-filtered live stream, and price
 * observation persistence for known markets only.
 */
class TickerEventPublisherTest {

    private final MarketRepository marketRepository = mock(MarketRepository.class);
    private final PriceObservationRepository priceObservationRepository =
            mock(PriceObservationRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"),
            ZoneOffset.UTC);

    private TickerEventPublisher publisher;

    private final UUID marketId = UUID.randomUUID();
    private Market market;

    @BeforeEach
    void setUp() {
        publisher = new TickerEventPublisher(
                marketRepository, priceObservationRepository, clock);
        market = new Market();
        market.setMarketId(marketId);
        market.setProvider(MarketProvider.KRAKEN);
        market.setSymbol("BTC/EUR");
        market.setBaseAsset("BTC");
        market.setQuoteAsset("EUR");
        when(marketRepository.findById(marketId)).thenReturn(Optional.of(market));
    }

    private TickerEvent ticker(String symbol, String bid) {
        return new TickerEvent(marketId, MarketProvider.KRAKEN, symbol,
                new BigDecimal(bid), new BigDecimal(bid).add(BigDecimal.ONE),
                new BigDecimal(bid).add(BigDecimal.TEN), BigDecimal.ONE,
                Instant.now());
    }

    @Test
    void lateSubscriberReceivesLatestSnapshotThenLiveEvents() {
        publisher.publish(ticker("btc/eur", "100"));

        List<TickerEvent> received = new java.util.ArrayList<>();
        publisher.streamBySymbol("BTC/EUR")
                .take(Duration.ofMillis(200))
                .subscribe(received::add);

        publisher.publish(ticker("BTC/EUR", "101"));

        assertThat(received).hasSize(2);
        assertThat(received.get(0).bid()).isEqualByComparingTo("100");
        assertThat(received.get(1).bid()).isEqualByComparingTo("101");
    }

    @Test
    void streamIsFilteredBySymbolAndNormalizesCase() {
        publisher.publish(ticker("BTC/EUR", "100"));

        List<TickerEvent> received = new java.util.ArrayList<>();
        publisher.streamBySymbol("eth/eur")
                .take(Duration.ofMillis(200))
                .subscribe(received::add);

        publisher.publish(ticker("ETH/EUR", "3000"));
        publisher.publish(ticker("BTC/EUR", "102"));

        // The BTC event must not leak into the ETH stream.
        assertThat(received).noneSatisfy(event ->
                assertThat(event.symbol()).isEqualTo("BTC/EUR"));
    }

    @Test
    void eventsWithoutSymbolAreIgnored() {
        TickerEvent event = spy(ticker("BTC/EUR", "1"));
        doReturn(null).when(event).symbol();

        publisher.publish(event);

        assertThat(publisher.latestByMarketId(marketId)).isEmpty();
    }

    @Test
    void streamByMarketIdDoesNotMixSameSymbolAcrossProviders() {
        UUID otherMarketId = UUID.randomUUID();
        TickerEvent target = ticker("EUR/USD", "100");
        TickerEvent other = new TickerEvent(otherMarketId, MarketProvider.KRAKEN, "EUR/USD",
                new BigDecimal("200"), new BigDecimal("201"), new BigDecimal("200.5"),
                BigDecimal.ONE, Instant.now());

        publisher.publish(target);
        List<TickerEvent> received = new java.util.ArrayList<>();
        publisher.streamByMarketId(marketId).take(Duration.ofMillis(200)).subscribe(received::add);
        publisher.publish(other);
        publisher.publish(target);

        assertThat(received).extracting(TickerEvent::marketId).containsOnly(marketId);
    }

    @Test
    void observationsArePersistedOnlyForKnownMarkets() {
        publisher.publish(ticker("BTC/EUR", "100"));
        verify(priceObservationRepository).save(any());
        assertThat(market.getMarketState().isTradable()).isTrue();
        verify(marketRepository).save(market);

        var unknownMarketEvent = new TickerEvent(
                UUID.randomUUID(), null, "DOGE/EUR",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, Instant.now());
        publisher.publish(unknownMarketEvent);

        verify(priceObservationRepository, times(1)).save(any());
    }

    @Test
    void incompleteEventsAreNotPersisted() {
        var missingOccurredAt = new TickerEvent(
                marketId, MarketProvider.KRAKEN, "BTC/EUR",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, null);
        publisher.publish(missingOccurredAt);

        Mockito.verifyNoInteractions(priceObservationRepository);
        // State is still updated; only persistence is skipped.
        assertThat(publisher.latestByMarketId(marketId)).isPresent();
    }
}
