package com.hope.trading.trading_core.valuation;

import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotDto;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.market_data.helper.MarketProvider;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.service.TradingCalculatorServiceImpl;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import com.hope.trading.trading_core.valuation.application.PaperDerivativeAccountValuationService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaperDerivativeAccountValuationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private final UUID marketId = UUID.randomUUID();
    private final MarketDataClient marketData = mock(MarketDataClient.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PaperDerivativeAccountValuationService service = new PaperDerivativeAccountValuationService(
            marketData, accounts, new TradingCalculatorServiceImpl(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void recomputesRepeatedValuationsWithoutAccumulatingUnrealizedPnl() {
        Account account = account();
        Trade trade = trade(TradeType.BUY, "100", "1");
        account.addTrade(trade);
        when(marketData.findAll()).thenReturn(List.of(market()));
        when(marketData.findPriceSnapshots(any())).thenReturn(List.of(price("105")), List.of(price("110")));
        when(accounts.save(account)).thenReturn(account);

        service.refresh(account);
        assertThat(account.getEquity()).isEqualByComparingTo("5005");
        service.refresh(account);
        assertThat(account.getEquity()).isEqualByComparingTo("5010");
        assertThat(account.getRealizedBalance()).isEqualByComparingTo("5000");
    }

    @Test
    void supportsPositiveToNegativeReversal() {
        Account account = account();
        account.addTrade(trade(TradeType.BUY, "100", "1"));
        when(marketData.findAll()).thenReturn(List.of(market()));
        when(marketData.findPriceSnapshots(any())).thenReturn(List.of(price("150")), List.of(price("70")));
        when(accounts.save(account)).thenReturn(account);

        service.refresh(account);
        assertThat(account.getEquity()).isEqualByComparingTo("5050");
        service.refresh(account);
        assertThat(account.getEquity()).isEqualByComparingTo("4970");
    }

    @Test
    void aggregatesLongShortAndIgnoresClosedTrades() {
        Account account = account();
        account.addTrade(trade(TradeType.BUY, "100", "2"));
        account.addTrade(trade(TradeType.SELL, "200", "1"));
        Trade closed = trade(TradeType.BUY, "1", "1");
        closed.setTradeStatus(TradeStatus.CLOSED);
        closed.setPnl(new BigDecimal("9999"));
        account.addTrade(closed);
        when(marketData.findAll()).thenReturn(List.of(market()));
        when(marketData.findPriceSnapshots(any())).thenReturn(List.of(price(marketId, "120")));

        service.refresh(account);

        assertThat(account.getEquity()).isEqualByComparingTo("5120");
    }

    @Test
    void staleDataDoesNotOverwriteAccountFacts() {
        Account account = account();
        account.addTrade(trade(TradeType.BUY, "100", "1"));
        account.setEquity(new BigDecimal("5010"));
        when(marketData.findAll()).thenReturn(List.of(market()));
        when(marketData.findPriceSnapshots(any())).thenReturn(List.of(new MarketPriceSnapshotDto(
                marketId, "BTC/USD", new BigDecimal("105"), new BigDecimal("104"),
                new BigDecimal("106"), true, NOW, MarketPriceSnapshotStatus.STALE)));

        assertThatThrownBy(() -> service.refresh(account)).isInstanceOf(IllegalStateException.class);
        assertThat(account.getEquity()).isEqualByComparingTo("5010");
        verify(accounts, never()).save(any());
    }

    @Test
    void invalidLaterMarketDoesNotMutateEarlierTradeMarks() {
        Account account = account();
        Trade first = trade(TradeType.BUY, "BTC/USD", "100", "1");
        Trade second = trade(TradeType.SELL, "ETH/USD", "200", "1");
        account.addTrade(first);
        account.addTrade(second);
        MarketResponse eth = market("ETH/USD");
        when(marketData.findAll()).thenReturn(List.of(market(), eth));
        when(marketData.findPriceSnapshots(any())).thenReturn(List.of(
                price(marketId, "110"),
                new MarketPriceSnapshotDto(eth.getMarketId(), "ETH/USD", new BigDecimal("190"),
                        new BigDecimal("189"), new BigDecimal("191"), true, NOW,
                        MarketPriceSnapshotStatus.STALE)));

        assertThatThrownBy(() -> service.refresh(account)).isInstanceOf(IllegalStateException.class);
        assertThat(first.getCurrentPrice()).isEqualByComparingTo("100");
        assertThat(second.getCurrentPrice()).isEqualByComparingTo("200");
        assertThat(account.getEquity()).isEqualByComparingTo("5000");
        verify(accounts, never()).save(any());
    }

    @Test
    void optimisticConflictPreventsStaleValuationPersistence() {
        Account account = account();
        account.addTrade(trade(TradeType.BUY, "100", "1"));
        when(marketData.findAll()).thenReturn(List.of(market()));
        when(marketData.findPriceSnapshots(any())).thenReturn(List.of(price("110")));
        when(accounts.save(account)).thenThrow(new OptimisticLockingFailureException("stale account"));

        assertThatThrownBy(() -> service.refresh(account))
                .isInstanceOf(OptimisticLockingFailureException.class);
        verify(accounts).save(account);
    }

    private Account account() {
        return Account.builder().accountId(UUID.randomUUID()).broker("KRAKEN").baseCurrency("USD")
                .settlementModel(SettlementModel.CASH).realizedBalance(new BigDecimal("5000"))
                .equity(new BigDecimal("5000")).peakEquity(new BigDecimal("5000"))
                .build();
    }

    private Trade trade(TradeType type, String entry, String quantity) {
        return trade(type, "BTC/USD", entry, quantity);
    }

    private Trade trade(TradeType type, String symbol, String entry, String quantity) {
        return Trade.builder().tradeId(UUID.randomUUID()).symbol(symbol).type(type)
                .entryPrice(new BigDecimal(entry)).quantity(new BigDecimal(quantity))
                .currentPrice(new BigDecimal(entry)).tradeStatus(TradeStatus.OPEN).build();
    }

    private MarketResponse market() {
        return market("BTC/USD");
    }

    private MarketResponse market(String symbol) {
        return MarketResponse.builder().marketId(symbol.equals("BTC/USD") ? marketId : UUID.randomUUID())
                .provider(MarketProvider.KRAKEN).symbol(symbol)
                .quoteAsset("USD").productType(InstrumentProductType.LINEAR_PERPETUAL).build();
    }

    private MarketPriceSnapshotDto price(String value) {
        return price(marketId, value);
    }

    private MarketPriceSnapshotDto price(UUID id, String value) {
        BigDecimal midpoint = new BigDecimal(value);
        return new MarketPriceSnapshotDto(id, "BTC/USD", midpoint,
                midpoint.subtract(BigDecimal.ONE), midpoint.add(BigDecimal.ONE), true,
                NOW, MarketPriceSnapshotStatus.FRESH);
    }
}
