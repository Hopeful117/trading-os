package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.dto.trade.KrakenTradeData;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.TradeEvent;
import com.hope.trading.market_data.model.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class KrakenTradeMapperTest {
    private static final Instant OCCURRED_AT =
            Instant.parse("2026-07-28T12:00:00.123456Z");

    private final KrakenTradeMapper mapper =
            new KrakenTradeMapper();
    private final Market market = Market.builder()
            .marketId(UUID.randomUUID())
            .provider(MarketProvider.KRAKEN)
            .symbol("BTC/EUR")
            .build();

    @Test
    void mapsBuyTakerSideAndCalculatesNotional() {
        TradeEvent event = mapper.toEvent(
                trade("buy", "101.25", "2.5", 42L),
                market
        );

        assertThat(event.side()).isEqualTo(TradeSide.BUY);
        assertThat(event.tradeId()).isEqualTo("42");
        assertThat(event.price()).isEqualByComparingTo("101.25");
        assertThat(event.quantity()).isEqualByComparingTo("2.5");
        assertThat(event.notional()).isEqualByComparingTo("253.125");
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void mapsSellTakerSide() {
        TradeEvent event = mapper.toEvent(
                trade("SELL", "99", "3", 43L),
                market
        );

        assertThat(event.side()).isEqualTo(TradeSide.SELL);
        assertThat(event.notional()).isEqualByComparingTo("297");
    }

    @Test
    void rejectsInvalidTradeWithoutProducingDomainEvent() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.toEvent(
                        trade("unknown", "100", "1", 44L),
                        market
                ))
                .withMessage("Unsupported Kraken trade side: unknown");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.toEvent(
                        trade("buy", "100", "0", 45L),
                        market
                ))
                .withMessage("Kraken trade quantity must be positive");
    }

    private KrakenTradeData trade(
            String side,
            String price,
            String quantity,
            Long tradeId
    ) {
        return new KrakenTradeData(
                "BTC/EUR",
                side,
                new BigDecimal(quantity),
                new BigDecimal(price),
                "market",
                tradeId,
                OCCURRED_AT
        );
    }
}
