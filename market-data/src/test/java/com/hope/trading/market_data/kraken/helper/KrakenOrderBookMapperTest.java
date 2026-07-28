package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.dto.KrakenMessageType;
import com.hope.trading.market_data.kraken.dto.orderbook.KrakenOrderBookData;
import com.hope.trading.market_data.kraken.dto.orderbook.KrakenOrderBookLevel;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OrderBookDelta;
import com.hope.trading.market_data.model.OrderBookDeltaType;
import com.hope.trading.market_data.model.OrderBookLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KrakenOrderBookMapperTest {
    private final KrakenOrderBookMapper mapper =
            new KrakenOrderBookMapper();

    @Test
    void mapsKrakenSnapshotToProviderIndependentDelta() {
        UUID marketId = UUID.randomUUID();
        Market market = Market.builder()
                .marketId(marketId)
                .provider(MarketProvider.KRAKEN)
                .symbol("BTC/EUR")
                .build();
        Instant timestamp = Instant.parse("2026-07-28T12:00:00Z");
        KrakenOrderBookData data = new KrakenOrderBookData(
                "BTC/EUR",
                List.of(new KrakenOrderBookLevel(
                        new BigDecimal("100"),
                        new BigDecimal("2")
                )),
                List.of(new KrakenOrderBookLevel(
                        new BigDecimal("101"),
                        new BigDecimal("3")
                )),
                123456L,
                timestamp
        );

        OrderBookDelta delta = mapper.toDelta(
                data,
                KrakenMessageType.SNAPSHOT,
                market,
                10
        );

        assertThat(delta.marketId()).isEqualTo(marketId);
        assertThat(delta.provider()).isEqualTo(MarketProvider.KRAKEN);
        assertThat(delta.symbol()).isEqualTo("BTC/EUR");
        assertThat(delta.depth()).isEqualTo(10);
        assertThat(delta.type()).isEqualTo(OrderBookDeltaType.SNAPSHOT);
        assertThat(delta.bids()).containsExactly(
                new OrderBookLevel(
                        new BigDecimal("100"),
                        new BigDecimal("2")
                )
        );
        assertThat(delta.asks()).containsExactly(
                new OrderBookLevel(
                        new BigDecimal("101"),
                        new BigDecimal("3")
                )
        );
        assertThat(delta.checksum()).isEqualTo(123456L);
        assertThat(delta.occurredAt()).isEqualTo(timestamp);
    }
}
