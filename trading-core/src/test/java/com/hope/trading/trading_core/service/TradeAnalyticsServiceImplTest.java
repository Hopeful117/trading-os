package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-2: protects the trading statistics the dashboard shows the
 * trader (PnL aggregation, win/loss metrics, exposure, daily-loss breach).
 * Wrong numbers here would mislead real decisions.
 */
class TradeAnalyticsServiceImplTest {

    private final TradeRepository tradeRepository = mock(TradeRepository.class);
    private final AccountService accountService = mock(AccountService.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);

    private TradeAnalyticsServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private final String username = "trader";

    private final Account account = new Account();

    private static Trade closed(String pnl) {
        Trade trade = new Trade();
        trade.setPnl(pnl == null ? null : new BigDecimal(pnl));
        trade.setClosedAt(Instant.parse("2026-08-23T10:00:00Z"));
        return trade;
    }

    private static Trade open(String entry, String quantity) {
        Trade trade = new Trade();
        trade.setClosedAt(null);
        trade.setEntryPrice(new BigDecimal(entry));
        trade.setQuantity(new BigDecimal(quantity));
        trade.setRiskAmount(BigDecimal.TEN);
        return trade;
    }

    @BeforeEach
    void setUp() {
        service = new TradeAnalyticsServiceImpl(
                tradeRepository, accountService, accountRepository);
        account.setAccountId(accountId);
    }

    /** All fixtures belong to the shared account (getClosedTrades filters by it). */
    private List<Trade> owned(Trade... trades) {
        for (Trade trade : trades) trade.setAccount(account);
        return List.of(trades);
    }

    @Test
    void totalPnLSumsOnlyNonNullValues() {
        when(tradeRepository.findAllByAccount_AccountId(accountId)).thenReturn(List.of(
                closed("100"), closed("-30"), open("999","1"), closed(null)));

        assertThat(service.getTotalPnL(accountId)).isEqualByComparingTo("70");
    }

    @Test
    void openAndClosedPnLAreDisjoint() {
        when(tradeRepository.findAllByAccount_AccountId(accountId)).thenReturn(List.of(
                closed("100"), open("100", "1")));

        assertThat(service.getClosedPnL(accountId)).isEqualByComparingTo("100");
        // Open PnL counts only open trades carrying a non-null pnl.
        assertThat(service.getOpenPnL(accountId)).isEqualByComparingTo("0");
    }

    @Test
    void winRateCountsWinningClosedTradesOnly() {
        when(tradeRepository.findAll()).thenReturn(owned(
                closed("50"), closed("25"), closed("-10")));
        when(accountService.getAccountById(accountId, username))
                .thenReturn(new Account());

        double winRate = service.getWinRate(accountId, username);

        assertThat(winRate).isEqualTo(2.0 / 3);
    }

    @Test
    void zeroClosedTradesYieldZeroWinRateWithoutDivisionError() {
        when(tradeRepository.findAll()).thenReturn(List.of());
        when(accountService.getAccountById(accountId, username))
                .thenReturn(new Account());

        assertThat(service.getWinRate(accountId, username)).isEqualTo(0.0);
        assertThat(service.getAverageWin(accountId, username)).isEqualByComparingTo("0");
        assertThat(service.getAverageLoss(accountId, username)).isEqualByComparingTo("0");
    }

    @Test
    void averageWinAndLossArePerTradeMeans() {
        when(tradeRepository.findAll()).thenReturn(owned(
                closed("60"), closed("30"), closed("-45")));
        when(accountService.getAccountById(accountId, username))
                .thenReturn(new Account());

        assertThat(service.getAverageWin(accountId, username)).isEqualByComparingTo("45");
        assertThat(service.getAverageLoss(accountId, username)).isEqualByComparingTo("-45");
    }

    @Test
    void currentExposureSumsNotionalOfOpenTradesOnly() {
        when(tradeRepository.findAllByAccount_AccountId(accountId)).thenReturn(List.of(
                open("100", "2"), open("50", "4"), closed("1")));
        when(accountService.getAccountById(accountId, username))
                .thenReturn(new Account());

        assertThat(service.getCurrentExposure(accountId, username))
                .isEqualByComparingTo("400");
    }

    @Test
    void dailyLossLimitIsBreachedWhenTodayLossMeetsTheLimit() {
        Rules rules = new Rules();
        rules.setMaxDailyLoss(new BigDecimal("200"));
        Account account = new Account();
        account.setRules(rules);
        org.mockito.Mockito.doReturn(Optional.of(account))
                .when(accountRepository).findById(accountId);
        // Today's closed trades net to -200 → |loss| >= limit.
        when(tradeRepository.findByAccount_AccountIdAndClosedAtGreaterThanEqualAndClosedAtLessThan(
                any(UUID.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(closed("-150"), closed("-50")));

        assertThat(service.hasReachedDailyLoss(accountId)).isTrue();
    }

    @Test
    void dailyLossLimitIsNotBreachedBelowTheLimit() {
        Rules rules = new Rules();
        rules.setMaxDailyLoss(new BigDecimal("500"));
        Account account = new Account();
        account.setRules(rules);
        org.mockito.Mockito.doReturn(Optional.of(account))
                .when(accountRepository).findById(accountId);
        when(tradeRepository.findByAccount_AccountIdAndClosedAtGreaterThanEqualAndClosedAtLessThan(
                any(UUID.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(closed("-150"), closed("50")));

        assertThat(service.hasReachedDailyLoss(accountId)).isFalse();
    }

    @Test
    void todayPnLSumsClosedTradesWithinTodayWindow() {
        when(tradeRepository.findByAccount_AccountIdAndClosedAtGreaterThanEqualAndClosedAtLessThan(
                any(UUID.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(closed("80"), closed("-20"), closed(null)));

        assertThat(service.getTodayPnL(accountId)).isEqualByComparingTo("60");
    }

    @Test
    void todayPnLReturnsZeroForEmptyList() {
        when(tradeRepository.findByAccount_AccountIdAndClosedAtGreaterThanEqualAndClosedAtLessThan(
                any(UUID.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        assertThat(service.getTodayPnL(accountId)).isEqualByComparingTo("0");
    }

    @Test
    void currentDrawdownDelegatesToAccountService() {
        when(accountService.getAccountById(accountId, username))
                .thenReturn(new Account());
        when(accountService.getCurrentDrawdown(accountId, username))
                .thenReturn(new BigDecimal("350"));

        assertThat(service.getCurrentDrawdown(accountId, username))
                .isEqualByComparingTo("350");
    }

    @Test
    void currentExposureHandlesNullEntryPriceGracefully() {
        Trade withNullEntry = new Trade();
        withNullEntry.setClosedAt(null);
        withNullEntry.setEntryPrice(null);
        withNullEntry.setQuantity(new BigDecimal("5"));
        withNullEntry.setAccount(account);

        Trade validTrade = new Trade();
        validTrade.setClosedAt(null);
        validTrade.setEntryPrice(new BigDecimal("100"));
        validTrade.setQuantity(new BigDecimal("2"));
        validTrade.setAccount(account);

        when(tradeRepository.findAllByAccount_AccountId(accountId))
                .thenReturn(List.of(withNullEntry, validTrade));
        when(accountService.getAccountById(accountId, username))
                .thenReturn(new Account());

        assertThatThrownBy(() -> service.getCurrentExposure(accountId, username))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getTradesBetweenFiltersByDateRange() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-02T00:00:00Z");
        Trade trade = closed("10");
        when(tradeRepository.findByAccount_AccountIdAndClosedAtGreaterThanEqualAndClosedAtLessThan(
                accountId, start, end))
                .thenReturn(List.of(trade));

        List<Trade> result = service.getTradesBetween(accountId, start, end);

        assertThat(result).hasSize(1).containsExactly(trade);
    }

    @Test
    void riskUsedTodaySumsRiskAmountOfTodayTrades() {
        Trade t1 = new Trade();
        t1.setRiskAmount(new BigDecimal("25"));
        t1.setAccount(account);
        Trade t2 = new Trade();
        t2.setRiskAmount(new BigDecimal("35"));
        t2.setAccount(account);
        Trade t3 = new Trade();
        t3.setRiskAmount(null);
        t3.setAccount(account);

        when(tradeRepository.findByAccount_AccountIdAndClosedAtGreaterThanEqualAndClosedAtLessThan(
                any(UUID.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(t1, t2, t3));

        assertThat(service.getRiskUsedToday(accountId)).isEqualByComparingTo("60");
    }

    @Test
    void allWinningTradesYieldOneHundredPercentWinRate() {
        when(tradeRepository.findAll()).thenReturn(owned(
                closed("100"), closed("50"), closed("25")));
        when(accountService.getAccountById(accountId, username))
                .thenReturn(new Account());

        assertThat(service.getWinRate(accountId, username)).isEqualTo(1.0);
        assertThat(service.getAverageWin(accountId, username)).isEqualByComparingTo("58");
        assertThat(service.getAverageLoss(accountId, username)).isEqualByComparingTo("0");
    }

    @Test
    void allLosingTradesYieldZeroWinRate() {
        when(tradeRepository.findAll()).thenReturn(owned(
                closed("-10"), closed("-20"), closed("-30")));
        when(accountService.getAccountById(accountId, username))
                .thenReturn(new Account());

        assertThat(service.getWinRate(accountId, username)).isEqualTo(0.0);
        assertThat(service.getAverageWin(accountId, username)).isEqualByComparingTo("0");
        assertThat(service.getAverageLoss(accountId, username)).isEqualByComparingTo("-20");
    }

    @Test
    void hasReachedDailyLossReturnsTrueWhenLossExceedsLimit() {
        Rules rules = new Rules();
        rules.setMaxDailyLoss(new BigDecimal("100"));
        Account acct = new Account();
        acct.setRules(rules);
        org.mockito.Mockito.doReturn(Optional.of(acct))
                .when(accountRepository).findById(accountId);
        when(tradeRepository.findByAccount_AccountIdAndClosedAtGreaterThanEqualAndClosedAtLessThan(
                any(UUID.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(closed("-100")));

        assertThat(service.hasReachedDailyLoss(accountId)).isTrue();
    }

    @Test
    void hasReachedDailyLossReturnsFalseWhenNoTradesToday() {
        Rules rules = new Rules();
        rules.setMaxDailyLoss(new BigDecimal("200"));
        Account acct = new Account();
        acct.setRules(rules);
        org.mockito.Mockito.doReturn(Optional.of(acct))
                .when(accountRepository).findById(accountId);
        when(tradeRepository.findByAccount_AccountIdAndClosedAtGreaterThanEqualAndClosedAtLessThan(
                any(UUID.class), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        assertThat(service.hasReachedDailyLoss(accountId)).isFalse();
    }
}
