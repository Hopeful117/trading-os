package com.hope.trading.market_data.kraken.helper;

import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesChartCandle;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcInterval;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KrakenFuturesOhlcMapperTest {
    @Test
    void mapsChartCandleToProviderNeutralEventAndPreservesIdentity() {
        Instant fetchedAt = Instant.parse("2026-10-14T12:00:00Z");
        KrakenFuturesChartCandle candle = new KrakenFuturesChartCandle();
        candle.setTime(Instant.parse("2026-10-14T11:00:00Z").toEpochMilli());
        candle.setOpen(new BigDecimal("100"));
        candle.setHigh(new BigDecimal("110"));
        candle.setLow(new BigDecimal("90"));
        candle.setClose(new BigDecimal("105"));
        candle.setVolume(new BigDecimal("12"));
        candle.setVwap(new BigDecimal("102"));
        candle.setTrades(7);
        Market market = Market.builder().marketId(UUID.randomUUID())
                .provider(MarketProvider.KRAKEN).symbol("BTC/USD").providerSymbol("PF_XBTUSD")
                .productType(InstrumentProductType.LINEAR_PERPETUAL).build();

        var event = new KrakenFuturesOhlcMapper(
                Clock.fixed(fetchedAt, ZoneOffset.UTC)).toEvent(
                        candle, market, OhlcInterval.ONE_HOUR, fetchedAt);

        assertThat(event.marketId()).isEqualTo(market.getMarketId());
        assertThat(event.symbol()).isEqualTo("BTC/USD");
        assertThat(event.providerSymbol()).isEqualTo("PF_XBTUSD");
        assertThat(event.openTime()).isEqualTo(Instant.parse("2026-10-14T11:00:00Z"));
        assertThat(event.close()).isEqualByComparingTo("105");
        assertThat(event.volume()).isEqualByComparingTo("12");
        assertThat(event.vwap()).isEqualByComparingTo("102");
        assertThat(event.trades()).isEqualTo(7);
        assertThat(event.closed()).isTrue();
        assertThat(event.fetchedAt()).isEqualTo(fetchedAt);
    }
}
