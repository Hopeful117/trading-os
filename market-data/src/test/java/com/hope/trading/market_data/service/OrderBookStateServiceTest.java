package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.OrderBookDelta;
import com.hope.trading.market_data.model.OrderBookDeltaType;
import com.hope.trading.market_data.model.OrderBookKey;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderBookStateServiceTest {
    private static final UUID FIRST_MARKET =
            UUID.fromString("3ff91f78-dc74-4327-bb81-daa7df3fbc11");
    private static final UUID SECOND_MARKET =
            UUID.fromString("f63ddf99-3657-49df-b872-a940f47c99f6");
    private static final Instant NOW =
            Instant.parse("2026-07-28T12:00:00Z");

    private final OrderBookStateService service =
            new OrderBookStateService();

    @Test
    void initializesSortedLimitedImmutableSnapshotWithMetrics() {
        OrderBookSnapshot snapshot = service.initialize(delta(
                FIRST_MARKET,
                2,
                OrderBookDeltaType.SNAPSHOT,
                List.of(
                        level("100", "2"),
                        level("102", "1"),
                        level("101", "3")
                ),
                List.of(
                        level("105", "4"),
                        level("103", "2"),
                        level("104", "3")
                )
        ));

        assertThat(snapshot.bids())
                .extracting(OrderBookLevel::price)
                .containsExactly(bd("102"), bd("101"));
        assertThat(snapshot.asks())
                .extracting(OrderBookLevel::price)
                .containsExactly(bd("103"), bd("104"));
        assertThat(snapshot.bestBid()).isEqualByComparingTo("102");
        assertThat(snapshot.bestAsk()).isEqualByComparingTo("103");
        assertThat(snapshot.spread()).isEqualByComparingTo("1");
        assertThat(snapshot.bidVolume()).isEqualByComparingTo("4");
        assertThat(snapshot.askVolume()).isEqualByComparingTo("5");
        assertThat(snapshot.imbalance())
                .isEqualByComparingTo("0.4444444444");
        assertThatThrownBy(() ->
                snapshot.bids().add(level("99", "1"))
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void appliesAddModificationAndZeroQuantityDeletion() {
        service.initialize(delta(
                FIRST_MARKET,
                10,
                OrderBookDeltaType.SNAPSHOT,
                List.of(level("100", "2"), level("99", "1")),
                List.of(level("101", "3"), level("102", "4"))
        ));

        OrderBookSnapshot updated = service.update(delta(
                FIRST_MARKET,
                10,
                OrderBookDeltaType.UPDATE,
                List.of(
                        level("100", "5"),
                        level("99", "0"),
                        level("98", "7")
                ),
                List.of(
                        level("101", "0"),
                        level("100.5", "2")
                )
        )).orElseThrow();

        assertThat(updated.bids())
                .containsExactly(
                        level("100", "5"),
                        level("98", "7")
                );
        assertThat(updated.asks())
                .containsExactly(
                        level("100.5", "2"),
                        level("102", "4")
                );
        assertThat(updated.spread()).isEqualByComparingTo("0.5");
    }

    @Test
    void ignoresUpdateBeforeSnapshot() {
        assertThat(service.update(delta(
                FIRST_MARKET,
                10,
                OrderBookDeltaType.UPDATE,
                List.of(level("100", "1")),
                List.of()
        ))).isEmpty();
    }

    @Test
    void isolatesMarketsAndDepthsAndCleansOnlyRequestedState() {
        service.initialize(delta(
                FIRST_MARKET,
                10,
                OrderBookDeltaType.SNAPSHOT,
                List.of(level("100", "1")),
                List.of(level("101", "1"))
        ));
        service.initialize(delta(
                FIRST_MARKET,
                25,
                OrderBookDeltaType.SNAPSHOT,
                List.of(level("200", "2")),
                List.of(level("201", "2"))
        ));
        service.initialize(delta(
                SECOND_MARKET,
                10,
                OrderBookDeltaType.SNAPSHOT,
                List.of(level("300", "3")),
                List.of(level("301", "3"))
        ));

        OrderBookSnapshot firstDepth = service.update(delta(
                FIRST_MARKET,
                10,
                OrderBookDeltaType.UPDATE,
                List.of(level("100", "4")),
                List.of()
        )).orElseThrow();
        OrderBookSnapshot secondDepth = service.update(delta(
                FIRST_MARKET,
                25,
                OrderBookDeltaType.UPDATE,
                List.of(),
                List.of()
        )).orElseThrow();
        OrderBookSnapshot secondMarket = service.update(delta(
                SECOND_MARKET,
                10,
                OrderBookDeltaType.UPDATE,
                List.of(),
                List.of()
        )).orElseThrow();

        assertThat(firstDepth.bestBid()).isEqualByComparingTo("100");
        assertThat(firstDepth.bidVolume()).isEqualByComparingTo("4");
        assertThat(secondDepth.bestBid()).isEqualByComparingTo("200");
        assertThat(secondMarket.bestBid()).isEqualByComparingTo("300");

        OrderBookKey cleared = new OrderBookKey(FIRST_MARKET, 10);
        service.clear(cleared);

        assertThat(service.hasState(cleared)).isFalse();
        assertThat(service.hasState(
                new OrderBookKey(FIRST_MARKET, 25)
        )).isTrue();
        assertThat(service.hasState(
                new OrderBookKey(SECOND_MARKET, 10)
        )).isTrue();
    }

    @Test
    void returnsNeutralImbalanceForEmptyBook() {
        OrderBookSnapshot snapshot = service.initialize(delta(
                FIRST_MARKET,
                10,
                OrderBookDeltaType.SNAPSHOT,
                List.of(),
                List.of()
        ));

        assertThat(snapshot.imbalance()).isEqualByComparingTo("0.5");
        assertThat(snapshot.bestBid()).isNull();
        assertThat(snapshot.bestAsk()).isNull();
        assertThat(snapshot.spread()).isNull();
    }

    private OrderBookDelta delta(
            UUID marketId,
            int depth,
            OrderBookDeltaType type,
            List<OrderBookLevel> bids,
            List<OrderBookLevel> asks
    ) {
        return new OrderBookDelta(
                marketId,
                MarketProvider.KRAKEN,
                marketId.equals(FIRST_MARKET) ? "BTC/EUR" : "ETH/EUR",
                depth,
                type,
                bids,
                asks,
                NOW,
                null
        );
    }

    private OrderBookLevel level(String price, String quantity) {
        return new OrderBookLevel(bd(price), bd(quantity));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
