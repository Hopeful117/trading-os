package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.RecentTradesSnapshot;
import com.hope.trading.market_data.model.TradeEvent;
import com.hope.trading.market_data.model.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecentTradesStateServiceTest {
    private static final UUID FIRST_MARKET =
            UUID.fromString("cc49249d-5f49-430a-881f-f7382cb86392");
    private static final UUID SECOND_MARKET =
            UUID.fromString("ed45d4d0-f54b-45a8-8921-e4424c69bfe4");
    private static final Instant GENERATED_AT =
            Instant.parse("2026-07-28T13:00:00Z");

    private final RecentTradesStateService service =
            new RecentTradesStateService(
                    Clock.fixed(GENERATED_AT, ZoneOffset.UTC)
            );

    @Test
    void addsTradeAndReturnsImmutableSnapshot() {
        RecentTradesSnapshot snapshot = service.add(
                trade(FIRST_MARKET, "1", 10)
        );

        assertThat(snapshot.marketId()).isEqualTo(FIRST_MARKET);
        assertThat(snapshot.trades())
                .containsExactly(trade(FIRST_MARKET, "1", 10));
        assertThat(snapshot.generatedAt()).isEqualTo(GENERATED_AT);
        assertThatThrownBy(() ->
                snapshot.trades().add(trade(FIRST_MARKET, "2", 11))
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void integratesBatchNewestFirstAndPreservesInputOrderForEqualTimestamps() {
        TradeEvent older = trade(FIRST_MARKET, "1", 10);
        TradeEvent firstAtSameTime = trade(FIRST_MARKET, "2", 12);
        TradeEvent secondAtSameTime = trade(FIRST_MARKET, "3", 12);
        TradeEvent newest = trade(FIRST_MARKET, "4", 15);

        RecentTradesSnapshot snapshot = service.addBatch(
                List.of(
                        older,
                        firstAtSameTime,
                        secondAtSameTime,
                        newest
                )
        );

        assertThat(snapshot.trades())
                .extracting(TradeEvent::tradeId)
                .containsExactly("4", "2", "3", "1");
    }

    @Test
    void deduplicatesByStableTradeId() {
        TradeEvent trade = trade(FIRST_MARKET, "duplicate", 10);

        service.add(trade);
        RecentTradesSnapshot snapshot = service.addBatch(
                List.of(trade, trade(FIRST_MARKET, "new", 11))
        );

        assertThat(snapshot.trades())
                .extracting(TradeEvent::tradeId)
                .containsExactly("new", "duplicate");
    }

    @Test
    void limitsBufferAndRemovesOldestTrades() {
        List<TradeEvent> trades = new ArrayList<>();

        for (int index = 0; index < 105; index++) {
            trades.add(trade(
                    FIRST_MARKET,
                    Integer.toString(index),
                    index
            ));
        }

        RecentTradesSnapshot snapshot = service.addBatch(trades);

        assertThat(snapshot.trades())
                .hasSize(RecentTradesStateService.MAX_RECENT_TRADES);
        assertThat(snapshot.trades().getFirst().tradeId())
                .isEqualTo("104");
        assertThat(snapshot.trades().getLast().tradeId())
                .isEqualTo("5");
        assertThat(snapshot.trades())
                .extracting(TradeEvent::tradeId)
                .doesNotContain("0", "1", "2", "3", "4");
    }

    @Test
    void isolatesMarketsAndClearsOnlyRequestedBuffer() {
        service.add(trade(FIRST_MARKET, "first", 10));
        service.add(trade(SECOND_MARKET, "second", 20));

        RecentTradesSnapshot first = service.add(
                trade(FIRST_MARKET, "new-first", 30)
        );
        RecentTradesSnapshot second = service.add(
                trade(SECOND_MARKET, "new-second", 40)
        );

        assertThat(first.trades())
                .extracting(TradeEvent::tradeId)
                .containsExactly("new-first", "first");
        assertThat(second.trades())
                .extracting(TradeEvent::tradeId)
                .containsExactly("new-second", "second");

        service.clear(FIRST_MARKET);

        assertThat(service.hasState(FIRST_MARKET)).isFalse();
        assertThat(service.hasState(SECOND_MARKET)).isTrue();
    }

    private TradeEvent trade(
            UUID marketId,
            String tradeId,
            long seconds
    ) {
        BigDecimal price = new BigDecimal("100");
        BigDecimal quantity = new BigDecimal("2");

        return new TradeEvent(
                marketId,
                MarketProvider.KRAKEN,
                marketId.equals(FIRST_MARKET) ? "BTC/EUR" : "ETH/EUR",
                tradeId,
                TradeSide.BUY,
                price,
                quantity,
                price.multiply(quantity),
                Instant.EPOCH.plusSeconds(seconds)
        );
    }
}
