package com.hope.trading.market_data.kraken.brokerClient;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResponse;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResult;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenRestOhlcEntry;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenAssetPairDto;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenAssetPairsResponse;
import com.hope.trading.market_data.kraken.helper.KrakenMarketMapper;
import com.hope.trading.market_data.kraken.helper.KrakenRestOhlcMapper;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.service.OhlcHistoryNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KrakenMarketDataTest {

    @Mock
    private KrakenHttpClient client;
    @Mock
    private KrakenMarketMapper marketMapper;
    @Mock
    private KrakenRestOhlcMapper restOhlcMapper;
    @Mock
    private OhlcHistoryNormalizer historyNormalizer;

    private KrakenMarketData provider;

    @BeforeEach
    void setUp() {
        provider = new KrakenMarketData(
                client,
                marketMapper,
                restOhlcMapper,
                historyNormalizer
        );
    }

    @Test
    void mapsAvailableMarkets() {
        KrakenAssetPairDto firstDto = new KrakenAssetPairDto();
        KrakenAssetPairDto secondDto = new KrakenAssetPairDto();
        firstDto.setWsname("XBT/EUR");
        secondDto.setWsname("ETH/EUR");
        KrakenAssetPairsResponse response = new KrakenAssetPairsResponse();
        response.setResult(Map.of("XXBTZEUR", firstDto, "XETHZEUR", secondDto));

        Market firstMarket = market("XBT/EUR");
        Market secondMarket = market("ETH/EUR");
        when(client.getAssetPairs()).thenReturn(response);
        when(marketMapper.toDomain(firstDto)).thenReturn(firstMarket);
        when(marketMapper.toDomain(secondDto)).thenReturn(secondMarket);

        assertThat(provider.getMarkets())
                .containsExactlyInAnyOrder(firstMarket, secondMarket);
        assertThat(provider.getName()).isEqualTo(MarketProvider.KRAKEN);
    }

    @Test
    void mapsNormalizesAndKeepsOnlyRequestedLatestCandles() {
        Market market = market("XBT/EUR");
        OhlcInterval interval = OhlcInterval.ONE_MINUTE;
        Instant responseTimestamp = Instant.parse("2026-07-28T10:03:00Z");
        KrakenOhlcResponse response = new KrakenOhlcResponse(List.of(), null);
        List<KrakenRestOhlcEntry> entries = List.of(
                entry("2026-07-28T10:00:00Z"),
                entry("2026-07-28T10:01:00Z"),
                entry("2026-07-28T10:02:00Z")
        );
        KrakenOhlcResult result =
                new KrakenOhlcResult("XXBTZEUR", entries, responseTimestamp);
        OhlcEvent first = event(market, entries.get(0).openTime(), true);
        OhlcEvent second = event(market, entries.get(1).openTime(), true);
        OhlcEvent current = event(market, entries.get(2).openTime(), false);

        when(client.findOhlcHistory("XBT/EUR", 1)).thenReturn(response);
        when(restOhlcMapper.extract(response)).thenReturn(result);
        when(restOhlcMapper.toEvent(
                entries.get(0), market, interval, true, responseTimestamp
        )).thenReturn(first);
        when(restOhlcMapper.toEvent(
                entries.get(1), market, interval, true, responseTimestamp
        )).thenReturn(second);
        when(restOhlcMapper.toEvent(
                entries.get(2), market, interval, false, responseTimestamp
        )).thenReturn(current);
        when(historyNormalizer.fillMissingIntervals(
                List.of(first, second, current), interval
        )).thenReturn(List.of(first, second, current));

        assertThat(provider.findOhlcHistory(market, interval, 2))
                .containsExactly(second, current);
    }

    @Test
    void returnsEmptyHistoryWhenKrakenHasNoCandles() {
        Market market = market("XBT/EUR");
        KrakenOhlcResponse response = new KrakenOhlcResponse(List.of(), null);
        KrakenOhlcResult result = new KrakenOhlcResult(
                "XXBTZEUR",
                List.of(),
                Instant.parse("2026-07-28T10:00:00Z")
        );

        when(client.findOhlcHistory("XBT/EUR", 5)).thenReturn(response);
        when(restOhlcMapper.extract(response)).thenReturn(result);
        when(historyNormalizer.fillMissingIntervals(
                List.of(), OhlcInterval.FIVE_MINUTES
        )).thenReturn(List.of());

        assertThat(provider.findOhlcHistory(
                market,
                OhlcInterval.FIVE_MINUTES,
                10
        )).isEmpty();
        verify(restOhlcMapper, never()).toEvent(
                any(), any(), any(), any(Boolean.class), any()
        );
    }

    @Test
    void rejectsInvalidHistoryArgumentsBeforeCallingKraken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider.findOhlcHistory(
                        null,
                        OhlcInterval.ONE_MINUTE,
                        1
                ))
                .withMessage("Market is required");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider.findOhlcHistory(
                        market("XBT/EUR"),
                        null,
                        1
                ))
                .withMessage("OHLC interval is required");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider.findOhlcHistory(
                        market("XBT/EUR"),
                        OhlcInterval.ONE_MINUTE,
                        721
                ))
                .withMessage("OHLC history limit must be between 1 and 720");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider.findOhlcHistory(
                        market(" "),
                        OhlcInterval.ONE_MINUTE,
                        1
                ))
                .withMessage("Market provider symbol is required");

        verify(client, never()).findOhlcHistory(any(), any(Integer.class));
    }

    private Market market(String symbol) {
        return Market.builder()
                .marketId(UUID.randomUUID())
                .provider(MarketProvider.KRAKEN)
                .symbol(symbol)
                .build();
    }

    private KrakenRestOhlcEntry entry(String openTime) {
        return new KrakenRestOhlcEntry(
                Instant.parse(openTime),
                BigDecimal.ONE,
                BigDecimal.TWO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                3
        );
    }

    private OhlcEvent event(
            Market market,
            Instant openTime,
            boolean closed
    ) {
        return new OhlcEvent(
                market.getMarketId(),
                market.getProvider(),
                market.getSymbol(),
                OhlcInterval.ONE_MINUTE,
                openTime,
                openTime.plusSeconds(60),
                BigDecimal.ONE,
                BigDecimal.TWO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ONE,
                3,
                closed,
                openTime
        );
    }
}
