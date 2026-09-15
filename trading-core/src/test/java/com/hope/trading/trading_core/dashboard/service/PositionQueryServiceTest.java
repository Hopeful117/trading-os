package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.dashboard.integration.MarketDataDashboardMapper;
import com.hope.trading.trading_core.dashboard.model.PositionSource;
import com.hope.trading.trading_core.dashboard.model.PositionValuationStatus;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotDto;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotRequest;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.service.TradingCalculatorServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PositionQueryServiceTest {
    private final MarketDataClient marketDataClient = mock(MarketDataClient.class);
    private final PositionQueryService service = new PositionQueryService(
            marketDataClient,
            new MarketDataDashboardMapper(),
            new PositionValuationService(new TradingCalculatorServiceImpl())
    );

    @Test
    void valuesPaperLongAtBidAndShortAtAskFromLocalTrades() {
        UUID longMarketId = UUID.randomUUID();
        UUID shortMarketId = UUID.randomUUID();
        Account account = account();
        account.addTrade(trade(TradeType.BUY, "BTC/USD", "100", "2"));
        account.addTrade(trade(TradeType.SELL, "ETH/USD", "200", "3"));
        when(marketDataClient.findAll()).thenReturn(List.of(
                market(longMarketId, "BTC/USD", "USD"), market(shortMarketId, "ETH/USD", "USD")));
        when(marketDataClient.findPriceSnapshots(any())).thenReturn(List.of(
                price(longMarketId, "BTC/USD", "99", "101", "100"),
                price(shortMarketId, "ETH/USD", "199", "201", "200")));

        var result = service.findPaperPositions(account, Instant.now());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).source()).isEqualTo(PositionSource.TRADING_CORE);
        assertThat(result.get(0).currentPrice()).isEqualByComparingTo("99");
        assertThat(result.get(0).unrealizedPnl()).isEqualByComparingTo("-2");
        assertThat(result.get(1).currentPrice()).isEqualByComparingTo("201");
        assertThat(result.get(1).unrealizedPnl()).isEqualByComparingTo("-3");
        verify(marketDataClient, never()).findById(any());
    }

    @Test
    void keepsPaperPositionButMarksStaleValuationUnavailable() {
        UUID marketId = UUID.randomUUID();
        Account account = account();
        account.addTrade(trade(TradeType.BUY, "BTC/USD", "100", "2"));
        when(marketDataClient.findAll()).thenReturn(List.of(market(marketId, "BTC/USD", "USD")));
        when(marketDataClient.findPriceSnapshots(any())).thenReturn(List.of(
                new MarketPriceSnapshotDto(marketId, "BTC/USD", new BigDecimal("100"),
                        new BigDecimal("99"), new BigDecimal("101"), true, Instant.now(),
                        MarketPriceSnapshotStatus.STALE)));

        var result = service.findPaperPositions(account, Instant.now());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().currentPrice()).isNull();
        assertThat(result.getFirst().unrealizedPnl()).isNull();
        assertThat(result.getFirst().valuationStatus()).isEqualTo(PositionValuationStatus.STALE);
    }

    @Test
    void makesCurrencyMismatchUnavailableWithoutUsingLastPrice() {
        UUID marketId = UUID.randomUUID();
        Account account = account();
        account.addTrade(trade(TradeType.BUY, "BTC/EUR", "100", "2"));
        when(marketDataClient.findAll()).thenReturn(List.of(market(marketId, "BTC/EUR", "EUR")));
        when(marketDataClient.findPriceSnapshots(any())).thenReturn(List.of(
                price(marketId, "BTC/EUR", "99", "101", "100")));

        var result = service.findPaperPositions(account, Instant.now());

        assertThat(result.getFirst().currentPrice()).isNull();
        assertThat(result.getFirst().valuationStatus()).isEqualTo(PositionValuationStatus.UNSUPPORTED_CURRENCY);
    }

    @Test
    void keepsPaperPositionUnvaluedWhenMarketSymbolIsAmbiguous() {
        UUID firstMarketId = UUID.randomUUID();
        UUID secondMarketId = UUID.randomUUID();
        Account account = account();
        account.addTrade(trade(TradeType.BUY, "XBT/USD", "100", "2"));
        when(marketDataClient.findAll()).thenReturn(List.of(
                market(firstMarketId, "BTC/USD", "USD"), market(secondMarketId, "XBT/USD", "USD")));

        var result = service.findPaperPositions(account, Instant.now());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().currentPrice()).isNull();
        assertThat(result.getFirst().valuationStatus()).isEqualTo(PositionValuationStatus.UNKNOWN_MARKET);
        verify(marketDataClient, never()).findPriceSnapshots(any());
    }

    @Test
    void keepsFreshSnapshotWithoutTimestampUnvalued() {
        UUID marketId = UUID.randomUUID();
        Account account = account();
        account.addTrade(trade(TradeType.BUY, "BTC/USD", "100", "2"));
        when(marketDataClient.findAll()).thenReturn(List.of(market(marketId, "BTC/USD", "USD")));
        when(marketDataClient.findPriceSnapshots(any())).thenReturn(List.of(
                new MarketPriceSnapshotDto(marketId, "BTC/USD", new BigDecimal("100"),
                        new BigDecimal("99"), new BigDecimal("101"), true, null,
                        MarketPriceSnapshotStatus.FRESH)));

        var result = service.findPaperPositions(account, Instant.now());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().currentPrice()).isNull();
        assertThat(result.getFirst().unrealizedPnl()).isNull();
        assertThat(result.getFirst().valuationStatus()).isEqualTo(PositionValuationStatus.UNAVAILABLE);
    }

    private Account account() {
        return Account.builder()
                .accountId(UUID.randomUUID())
                .baseCurrency("USD")
                .equity(new BigDecimal("1000"))
                .peakEquity(new BigDecimal("1000"))
                .build();
    }

    private Trade trade(TradeType type, String symbol, String entry, String quantity) {
        return Trade.builder()
                .tradeId(UUID.randomUUID())
                .symbol(symbol)
                .type(type)
                .entryPrice(new BigDecimal(entry))
                .quantity(new BigDecimal(quantity))
                .tradeStatus(TradeStatus.OPEN)
                .openedAt(Instant.now())
                .build();
    }

    private MarketResponse market(UUID id, String symbol, String quoteAsset) {
        return MarketResponse.builder().marketId(id).symbol(symbol).quoteAsset(quoteAsset).build();
    }

    private MarketPriceSnapshotDto price(UUID id, String symbol, String bid, String ask, String last) {
        return new MarketPriceSnapshotDto(id, symbol, new BigDecimal(last), new BigDecimal(bid),
                new BigDecimal(ask), true, Instant.now(), MarketPriceSnapshotStatus.FRESH);
    }
}
