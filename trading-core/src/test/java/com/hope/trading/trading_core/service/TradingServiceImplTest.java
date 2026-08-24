package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeCalculation;
import com.hope.trading.trading_core.dto.TradeDto;
import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.TradeMapper;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-2: protects the trade lifecycle state machine that the trader
 * journey depends on — closing twice is impossible, partial closes accumulate
 * PnL and close at zero remaining quantity, equity updates are propagated,
 * and filters are honored.
 *
 * <p>NOTE (known product bug, separate story): {@code openTrade} currently
 * passes a null entry price into the calculator and always fails; only the
 * post-open lifecycle is asserted here.</p>
 */
class TradingServiceImplTest {

    private final TradeRepository tradeRepository = mock(TradeRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountService accountService = mock(AccountService.class);
    private final RiskEngine riskEngine = mock(RiskEngine.class);
    private final TradingCalculatorService calculator =
            new TradingCalculatorServiceImpl();
    private final TradeRequestValidator validator = new TradeRequestValidatorImpl();
    private final TradeMapper tradeMapper = new TradeMapper();

    private TradingServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private final UUID tradeId = UUID.randomUUID();
    private final String username = "trader";
    private Account account;

    @BeforeEach
    void setUp() {
        // Real calculator + validator: the arithmetic contract is part of the
        // behavior under test, not an implementation detail.
        service = new TradingServiceImpl(tradeRepository, accountRepository,
                accountService, riskEngine, calculator, validator, tradeMapper);

        account = new Account();
        account.setAccountId(accountId);
        account.setEquity(new BigDecimal("1000"));
    }

    private Trade openTrade(TradeType type) {
        Trade trade = new Trade();
        trade.setTradeId(tradeId);
        trade.setSymbol("BTC/USD");
        trade.setType(type);
        trade.setEntryPrice(new BigDecimal("100"));
        trade.setCurrentPrice(new BigDecimal("100"));
        trade.setQuantity(new BigDecimal("2"));
        trade.setStopLoss(new BigDecimal("95"));
        trade.setTakeProfit(new BigDecimal("110"));
        trade.setTradeStatus(TradeStatus.OPEN);
        trade.setAccount(account);
        when(tradeRepository.findById(tradeId)).thenReturn(Optional.of(trade));
        return trade;
    }

    @Test
    void closeTradeComputesPnlUpdatesEquityAndClosesState() {
        Trade trade = openTrade(TradeType.BUY);
        when(tradeRepository.save(any(Trade.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TradeDto dto = service.closeTrade(tradeId, new BigDecimal("110"), username);

        assertThat(dto.getExitPrice()).isEqualByComparingTo("110");
        assertThat(dto.getPnl()).isEqualByComparingTo("20");
        assertThat(dto.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
        assertThat(trade.getClosedAt()).isNotNull();
        Mockito.verify(accountService).updateEquity(
                accountId, new BigDecimal("20"), username);
    }

    @Test
    void shortTradeClosePnLIsDirectionAware() {
        openTrade(TradeType.SELL);
        when(tradeRepository.save(any(Trade.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TradeDto dto = service.closeTrade(tradeId, new BigDecimal("90"), username);

        assertThat(dto.getPnl()).isEqualByComparingTo("20");
    }

    @Test
    void closingAnAlreadyClosedTradeIsRejected() {
        Trade trade = openTrade(TradeType.BUY);
        trade.setClosedAt(Instant.parse("2026-08-23T09:00:00Z"));

        assertThatThrownBy(() -> service.closeTrade(
                tradeId, new BigDecimal("110"), username))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void unknownTradeCannotBeClosed() {
        when(tradeRepository.findById(tradeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.closeTrade(
                tradeId, new BigDecimal("110"), username))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Trade not found");
    }

    @Test
    void partialCloseReducesQuantityAndAccumulatesPnl() {
        Trade trade = openTrade(TradeType.BUY);
        trade.setPnl(null);
        when(tradeRepository.save(any(Trade.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TradeDto dto = service.partialClose(
                tradeId, new BigDecimal("1"), new BigDecimal("110"), username);

        assertThat(dto.getQuantity()).isEqualByComparingTo("1");
        assertThat(dto.getPnl()).isEqualByComparingTo("10");
        assertThat(dto.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
        Mockito.verify(accountService).updateEquity(
                accountId, new BigDecimal("10"), username);
    }

    @Test
    void fullPartialCloseClosesTheTrade() {
        Trade trade = openTrade(TradeType.BUY);
        when(tradeRepository.save(any(Trade.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.partialClose(tradeId, new BigDecimal("2"), new BigDecimal("105"), username);

        assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
        assertThat(trade.getClosedAt()).isNotNull();
    }

    @Test
    void partialCloseBeyondOpenQuantityIsRejected() {
        openTrade(TradeType.BUY);

        assertThatThrownBy(() -> service.partialClose(
                tradeId, new BigDecimal("3"), new BigDecimal("105"), username))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void filtersCombineTypeAndSymbol() {
        Trade buyBtc = openTrade(TradeType.BUY);
        Trade sellBtc = openTrade(TradeType.SELL);
        Trade buyEth = openTrade(TradeType.BUY);
        buyEth.setSymbol("ETH/USD");
        when(tradeRepository.findAllByAccount_AccountId(accountId))
                .thenReturn(List.of(buyBtc, sellBtc, buyEth));

        List<TradeDto> buysOnly = service.getTradesByFilters(accountId, TradeType.BUY, null);
        List<TradeDto> btcOnly = service.getTradesByFilters(accountId, null, "BTC/USD");
        List<TradeDto> all = service.getTradesByFilters(accountId, null, null);
        List<TradeDto> buyBtcExact = service.getTradesByFilters(accountId, TradeType.BUY, "BTC/USD");

        assertThat(buysOnly).extracting(TradeDto::getType)
                .containsOnly(TradeType.BUY);
        assertThat(btcOnly).allSatisfy(t -> assertThat(t.getSymbol()).isEqualTo("BTC/USD"));
        assertThat(all).hasSize(3);
        assertThat(buyBtcExact).hasSize(1);
    }

    @Test
    void stopLossAndTakeProfitUpdatesPersist() {
        Trade trade = openTrade(TradeType.BUY);
        when(tradeRepository.save(any(Trade.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TradeDto afterSl = service.updateStopLoss(tradeId, new BigDecimal("94"));
        assertThat(afterSl.getStopLoss()).isEqualByComparingTo("94");

        TradeDto afterTp = service.updateTakeProfit(tradeId, new BigDecimal("120"));
        assertThat(afterTp.getTakeProfit()).isEqualByComparingTo("120");
    }

    @Test
    void getTradeByIdFailsForUnknownId() {
        when(tradeRepository.findById(tradeId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTradeById(tradeId))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
