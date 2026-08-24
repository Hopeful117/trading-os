package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.OrderBookKey;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STORY-0020A-3B: protects the order-book fan-out contract — latest snapshot
 * replay for late subscribers, market/depth isolation, and state cleanup.
 */
class OrderBookEventPublisherTest {

    private final OrderBookEventPublisher publisher = new OrderBookEventPublisher();

    private static OrderBookSnapshot snapshot(UUID marketId, int depth, String bestBid) {
        return new OrderBookSnapshot(
                marketId, MarketProvider.KRAKEN, "BTC/EUR", depth,
                List.of(new OrderBookLevel(new BigDecimal(bestBid), BigDecimal.ONE)),
                List.of(new OrderBookLevel(new BigDecimal(bestBid).add(BigDecimal.TEN),
                        BigDecimal.ONE)),
                new BigDecimal(bestBid), new BigDecimal(bestBid).add(BigDecimal.TEN),
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, Instant.now());
    }

    @Test
    void lateSubscriberReceivesLatestSnapshotForItsMarketAndDepth() {
        UUID marketId = UUID.randomUUID();
        publisher.publish(snapshot(marketId, 10, "50000"));

        CopyOnWriteArrayList<OrderBookSnapshot> received = new CopyOnWriteArrayList<>();
        publisher.streamByMarketAndDepth(marketId, 10)
                .take(Duration.ofMillis(200))
                .subscribe(received::add);

        publisher.publish(snapshot(marketId, 10, "50100"));

        assertThat(received).hasSize(2);
        assertThat(received.get(0).bestBid()).isEqualByComparingTo("50000");
        assertThat(received.get(1).bestBid()).isEqualByComparingTo("50100");
    }

    @Test
    void snapshotsAreIsolatedByMarketAndDepth() {
        UUID marketA = UUID.randomUUID();
        UUID marketB = UUID.randomUUID();
        publisher.publish(snapshot(marketA, 10, "100"));
        publisher.publish(snapshot(marketB, 25, "3000"));

        CopyOnWriteArrayList<OrderBookSnapshot> received = new CopyOnWriteArrayList<>();
        publisher.streamByMarketAndDepth(marketA, 10)
                .take(Duration.ofMillis(200))
                .subscribe(received::add);

        publisher.publish(snapshot(marketB, 25, "3100"));

        // The market-B event must not leak into the market-A stream.
        assertThat(received).allSatisfy(snapshot ->
                assertThat(snapshot.marketId()).isEqualTo(marketA));
    }

    @Test
    void clearRemovesTheLatestStateWithoutAffectingLiveStream() {
        UUID marketId = UUID.randomUUID();
        publisher.publish(snapshot(marketId, 10, "50000"));

        publisher.clear(new OrderBookKey(marketId, 10));

        CopyOnWriteArrayList<OrderBookSnapshot> received = new CopyOnWriteArrayList<>();
        // After clear, a late subscriber gets no stale snapshot; only live
        // events are delivered.
        publisher.publish(snapshot(marketId, 10, "50200"));
        publisher.streamByMarketAndDepth(marketId, 10)
                .take(Duration.ofMillis(200))
                .subscribe(received::add);

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().bestBid()).isEqualByComparingTo("50200");
    }
}
