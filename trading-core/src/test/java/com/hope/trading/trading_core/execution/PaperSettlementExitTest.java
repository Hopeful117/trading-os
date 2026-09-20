package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.service.PaperSettlementService;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference;
import com.hope.trading.trading_core.execution.domain.model.TradePlanReference;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.*;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.service.TradingCalculatorServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaperSettlementExitTest {
    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

    @Test
    void longExitSettlesCashEquityAndGrossPnlOnce() {
        Scenario s = scenario(TradeType.BUY, "0", "0");
        s.settle("110", "0.5");

        assertThat(amount(s.account, "USD")).isEqualByComparingTo("10019.5");
        assertThat(amount(s.account, "BTC")).isZero();
        assertThat(s.account.getEquity()).isEqualByComparingTo("10019.5");
        assertThat(s.trade.getPnl()).isEqualByComparingTo("20");
        assertThat(s.trade.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
    }

    @Test
    void shortExitRestoresBorrowedFreeBaseWithoutInventingShorting() {
        Scenario s = scenario(TradeType.SELL, "2", "0");
        s.settle("90", "0");

        assertThat(amount(s.account, "USD")).isEqualByComparingTo("10020");
        assertThat(amount(s.account, "BTC")).isEqualByComparingTo("2");
        assertThat(s.account.getEquity()).isEqualByComparingTo("10020");
        assertThat(s.trade.getPnl()).isEqualByComparingTo("20");
        assertThat(s.trade.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
    }

    @Test
    void entrySettlementPersistsTheApprovedTradePlanStopLoss() {
        UUID owner = UUID.randomUUID();
        BrokerAccount broker = BrokerAccount.create(owner, BrokerProvider.KRAKEN, ExecutionMode.PAPER, "paper", NOW);
        Account account = Account.builder().accountId(UUID.randomUUID()).brokerAccountId(broker.id())
                .broker("KRAKEN").name("paper").baseCurrency("USD").equity(new BigDecimal("10000"))
                .peakEquity(new BigDecimal("10000")).user(User.builder().userId(owner).build()).build();
        account.addBalance(AccountBalance.builder().asset("USD").amount(new BigDecimal("10000")).build());

        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        TradePlanRiskPort plans = mock(TradePlanRiskPort.class);
        when(brokers.findById(broker.id())).thenReturn(Optional.of(broker));
        when(accounts.findByBrokerAccountId(broker.id())).thenReturn(Optional.of(account));
        when(accounts.save(account)).thenReturn(account);
        UUID planId = UUID.randomUUID();
        when(plans.load(planId, 1)).thenReturn(new TradePlanRiskPort.Snapshot(planId, 1, "ACCEPTED",
                NOW, UUID.randomUUID(), 1, NOW, owner, account.getAccountId(), "USD", UUID.randomUUID(), 1,
                UUID.randomUUID(), 1, "BTC/USD", "LONG", null, new BigDecimal("90"), BigDecimal.ONE,
                new BigDecimal("100"), new BigDecimal("10"), "USD", "{}"));

        PaperSettlementService settlement = new PaperSettlementService(brokers, accounts,
                new TradingCalculatorServiceImpl(), plans);
        ExecutionIntent intent = ExecutionIntent.create(ExecutionIntentId.newId(),
                new TradePlanReference(planId, 1),
                new RiskApprovalReference(UUID.randomUUID(), RiskApprovalReference.Decision.APPROVED, NOW),
                new IdempotencyKey("paper-entry-stop"), owner, broker.id(),
                new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, BigDecimal.ONE, null), NOW, NOW.plusSeconds(300));
        ExecutionAttempt attempt = ExecutionAttempt.create(ExecutionAttemptId.newId(), intent.id(), 1, NOW, null);
        BrokerOrder order = BrokerOrder.acknowledged(BrokerOrderId.newId(), intent.id(), attempt.id(), "SIM", NOW);
        order.addFill(new BrokerOrder.Fill("fill", BigDecimal.ONE, new BigDecimal("100"), BigDecimal.ZERO, NOW), true, NOW);

        settlement.settle(intent, attempt, order);

        assertThat(account.getTrades()).singleElement().satisfies(trade ->
                assertThat(trade.getStopLoss()).isEqualByComparingTo("90"));
    }

    private Scenario scenario(TradeType type, String initialBase, String entryFee) {
        UUID owner = UUID.randomUUID();
        BrokerAccount broker = BrokerAccount.create(owner, BrokerProvider.KRAKEN, ExecutionMode.PAPER, "paper", NOW);
        Account account = Account.builder().accountId(UUID.randomUUID()).brokerAccountId(broker.id())
                .broker("KRAKEN").name("paper").baseCurrency("USD").equity(new BigDecimal("10000"))
                .peakEquity(new BigDecimal("10000")).user(User.builder().userId(owner).build()).build();
        account.addBalance(AccountBalance.builder().asset("USD")
                .amount(new BigDecimal(type == TradeType.BUY ? "9800" : "10200")).build());
        BigDecimal currentBase = type == TradeType.BUY ? new BigDecimal("2") : BigDecimal.ZERO;
        account.addBalance(AccountBalance.builder().asset("BTC").amount(currentBase).build());
        Trade trade = Trade.builder().tradeId(UUID.randomUUID()).symbol("BTC/USD").type(type)
                .entryPrice(new BigDecimal("100")).quantity(new BigDecimal("2")).openedAt(NOW)
                .tradeStatus(TradeStatus.OPEN).build();
        account.addTrade(trade);

        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        when(brokers.findById(broker.id())).thenReturn(Optional.of(broker));
        when(accounts.findByBrokerAccountId(broker.id())).thenReturn(Optional.of(account));
        when(accounts.save(account)).thenReturn(account);
        return new Scenario(new PaperSettlementService(brokers, accounts, new TradingCalculatorServiceImpl()), account, trade,
                owner, broker, entryFee);
    }

    private static BigDecimal amount(Account account, String asset) {
        return account.getBalances().stream().filter(b -> asset.equals(b.getAsset())).map(AccountBalance::getAmount)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private record Scenario(PaperSettlementService settlement, Account account, Trade trade,
                            UUID owner, BrokerAccount broker, String entryFee) {
        void settle(String price, String fee) {
            ExecutionParameters.Side side = trade.getType() == TradeType.BUY
                    ? ExecutionParameters.Side.SELL : ExecutionParameters.Side.BUY;
            ExecutionIntent intent = ExecutionIntent.createExit(ExecutionIntentId.newId(), owner, broker.id(),
                    trade.getTradeId(), new ExecutionParameters("BTC/USD", side,
                            ExecutionParameters.OrderType.MARKET, trade.getQuantity(), null),
                    new IdempotencyKey(UUID.randomUUID().toString()), NOW, NOW.plusSeconds(300));
            ExecutionAttempt attempt = ExecutionAttempt.create(ExecutionAttemptId.newId(), intent.id(), 1, NOW, null);
            BrokerOrder order = BrokerOrder.acknowledged(BrokerOrderId.newId(), intent.id(), attempt.id(), "SIM", NOW);
            order.addFill(new BrokerOrder.Fill("fill", trade.getQuantity(), new BigDecimal(price),
                    new BigDecimal(fee), NOW), true, NOW);
            settlement.settle(intent, attempt, order);
        }
    }
}
