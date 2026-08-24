package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.RecentTradesSnapshot;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STORY-0020A-3B: protects the recent-trades fan-out contract — latest
 * snapshot replay, market isolation, and cleanup semantics.
 */
class RecentTradesEventPublisherTest {

    private final RecentTradesEventPublisher publisher = new RecentTradesEventPublisher();

    private static RecentTradesSnapshot snapshot(UUID marketId) {
        return new RecentTradesSnapshot(marketId,
                com.hope.trading.market_data.helper.MarketProvider.KRAKEN,
                "BTC/EUR", List.of(), Instant.now());
    }

    @Test
    void lateSubscriberReceivesLatestSnapshotThenLiveUpdates() {
        UUID marketId = UUID.randomUUID();
        publisher.publish(snapshot(marketId));

        CopyOnWriteArrayList<RecentTradesSnapshot> received = new CopyOnWriteArrayList<>();
        publisher.streamByMarket(marketId)
                .take(Duration.ofMillis(200))
                .subscribe(received::add);

        publisher.publish(snapshot(marketId));

        assertThat(received).hasSize(2);
    }

    @Test
    void snapshotsAreIsolatedByMarket() {
        UUID marketA = UUID.randomUUID();
        UUID marketB = UUID.randomUUID();
        publisher.publish(snapshot(marketA));
        publisher.publish(snapshot(marketB));

        CopyOnWriteArrayList<RecentTradesSnapshot> received = new CopyOnWriteArrayList<>();
        publisher.streamByMarket(marketA)
                .take(Duration.ofMillis(200))
                .subscribe(received::add);

        publisher.publish(snapshot(marketB));

        assertThat(received).allSatisfy(snapshot ->
                assertThat(snapshot.marketId()).isEqualTo(marketA));
    }

    @Test
    void clearRemovesTheLatestState() {
        UUID marketId = UUID.randomUUID();
        publisher.publish(snapshot(marketId));

        publisher.clear(marketId);

        CopyOnWriteArrayList<RecentTradesSnapshot> received = new CopyOnWriteArrayList<>();
        publisher.streamByMarket(marketId)
                .take(Duration.ofMillis(200))
                .subscribe(received::add);
        // No stale snapshot replayed after clear; only a live publish flows.
        publisher.publish(snapshot(marketId));

        assertThat(received).hasSize(1);
    }

    @Test
    void streamWithoutAnyPublishIsEmpty() {
        CopyOnWriteArrayList<RecentTradesSnapshot> received = new CopyOnWriteArrayList<>();
        publisher.streamByMarket(UUID.randomUUID())
                .take(Duration.ofMillis(100))
                .subscribe(received::add);

        assertThat(received).isEmpty();
    }
}
