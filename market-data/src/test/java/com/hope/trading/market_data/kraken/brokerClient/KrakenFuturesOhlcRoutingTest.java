package com.hope.trading.market_data.kraken.brokerClient;

import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesChartCandle;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesChartResponse;
import com.hope.trading.market_data.kraken.helper.KrakenFuturesMarketMapper;
import com.hope.trading.market_data.kraken.helper.KrakenFuturesOhlcMapper;
import com.hope.trading.market_data.kraken.helper.KrakenFuturesTickerMapper;
import com.hope.trading.market_data.kraken.helper.KrakenMarketMapper;
import com.hope.trading.market_data.kraken.helper.KrakenProviderSymbolResolver;
import com.hope.trading.market_data.kraken.helper.KrakenRestOhlcMapper;
import com.hope.trading.market_data.kraken.helper.KrakenRestTickerMapper;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.service.OhlcHistoryNormalizer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KrakenFuturesOhlcRoutingTest {
    @Test
    void usesChartsApiProviderSymbolAndTradeTicksForLinearPerpetual() {
        KrakenFuturesChartsHttpClient charts = mock(KrakenFuturesChartsHttpClient.class);
        KrakenHttpClient spot = mock(KrakenHttpClient.class);
        KrakenFuturesHttpClient futures = mock(KrakenFuturesHttpClient.class);
        OhlcHistoryNormalizer normalizer = mock(OhlcHistoryNormalizer.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
        var candle = new KrakenFuturesChartCandle();
        candle.setTime(Instant.parse("2026-10-14T11:00:00Z").toEpochMilli());
        candle.setOpen(new java.math.BigDecimal("100"));
        candle.setHigh(new java.math.BigDecimal("110"));
        candle.setLow(new java.math.BigDecimal("90"));
        candle.setClose(new java.math.BigDecimal("105"));
        candle.setVolume(new java.math.BigDecimal("2"));
        var response = new KrakenFuturesChartResponse();
        response.setCandles(List.of(candle));
        when(charts.getCandles("trade", "PF_XBTUSD", "1h", 5, null, null)).thenReturn(response);
        when(normalizer.fillMissingIntervals(anyList(), eq(OhlcInterval.ONE_HOUR)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var provider = new KrakenMarketData(spot, futures, charts, mock(KrakenMarketMapper.class),
                mock(KrakenFuturesMarketMapper.class), new KrakenProviderSymbolResolver(),
                mock(KrakenRestOhlcMapper.class), mock(KrakenRestTickerMapper.class),
                mock(KrakenFuturesTickerMapper.class), new KrakenFuturesOhlcMapper(clock), normalizer, clock);
        Market market = Market.builder().marketId(UUID.randomUUID()).provider(MarketProvider.KRAKEN)
                .symbol("BTC/USD").providerSymbol("PF_XBTUSD")
                .productType(InstrumentProductType.LINEAR_PERPETUAL).build();

        var snapshot = provider.findOhlcHistorySnapshot(market, OhlcInterval.ONE_HOUR, 5);

        assertThat(snapshot.observedEvents()).hasSize(1);
        assertThat(snapshot.observedEvents().getFirst().openTime())
                .isEqualTo(Instant.parse("2026-10-14T11:00:00Z"));
        verify(charts).getCandles("trade", "PF_XBTUSD", "1h", 5, null, null);
        verifyNoInteractions(spot);
    }
}
