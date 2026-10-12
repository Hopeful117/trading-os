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
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionAttemptId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.market_data.helper.MarketProvider;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.service.TradingCalculatorServiceImpl;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperCashDerivativeSettlementTest {
    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

    @Test
    void longEntryAndExitRealizeCashPnlAndFeesWithoutChangingBalances() {
        Scenario scenario = scenario();
        Map<String, BigDecimal> balancesBefore = balances(scenario.account);

        scenario.settleEntry(TradeType.BUY, "100", "1");
        assertThat(balances(scenario.account)).isEqualTo(balancesBefore);
        assertThat(scenario.account.getRealizedBalance()).isEqualByComparingTo("9999");
        assertThat(scenario.account.getEquity()).isEqualByComparingTo("9999");

        Trade trade = scenario.account.getTrades().getFirst();
        trade.setTradeId(UUID.randomUUID());
        scenario.settleExit(trade, "110", "2");

        assertThat(balances(scenario.account)).isEqualTo(balancesBefore);
        assertThat(scenario.account.getRealizedBalance()).isEqualByComparingTo("10017");
        assertThat(scenario.account.getEquity()).isEqualByComparingTo("10017");
        assertThat(trade.getPnl()).isEqualByComparingTo("20");
        assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
    }

    @Test
    void shortEntryAndExitRealizeCashPnlWithoutBorrowingUnderlying() {
        Scenario scenario = scenario();
        Map<String, BigDecimal> balancesBefore = balances(scenario.account);

        scenario.settleEntry(TradeType.SELL, "100", "1");
        Trade trade = scenario.account.getTrades().getFirst();
        trade.setTradeId(UUID.randomUUID());
        scenario.settleExit(trade, "90", "2");

        assertThat(balances(scenario.account)).isEqualTo(balancesBefore);
        assertThat(scenario.account.getEquity()).isEqualByComparingTo("10017");
        assertThat(trade.getPnl()).isEqualByComparingTo("20");
        assertThat(trade.getType()).isEqualTo(TradeType.SELL);
    }

    @Test
    void losingLongExitReducesCashEquityByGrossLossAndClosingFee() {
        Scenario scenario = scenario();
        scenario.settleEntry(TradeType.BUY, "100", "1");
        Trade trade = scenario.account.getTrades().getFirst();
        trade.setTradeId(UUID.randomUUID());
        scenario.settleExit(trade, "90", "2");

        assertThat(trade.getPnl()).isEqualByComparingTo("-20");
        assertThat(scenario.account.getEquity()).isEqualByComparingTo("9977");
    }

    @Test
    void repeatedExitIsRejectedWithoutRealizingPnlTwice() {
        Scenario scenario = scenario();
        scenario.settleEntry(TradeType.BUY, "100", "1");
        Trade trade = scenario.account.getTrades().getFirst();
        trade.setTradeId(UUID.randomUUID());
        scenario.settleExit(trade, "110", "2");

        assertThatThrownBy(() -> scenario.settleExit(trade, "110", "2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("EXIT target trade is already closed");
        assertThat(scenario.account.getEquity()).isEqualByComparingTo("10017");
    }

    @Test
    void cashSettlementFailureLeavesAccountUnchanged() {
        Scenario scenario = scenario();
        Map<String, BigDecimal> balancesBefore = balances(scenario.account);
        ExecutionIntent intent = ExecutionIntent.createExit(ExecutionIntentId.newId(), scenario.owner,
                scenario.broker.id(), UUID.randomUUID(), new ExecutionParameters("BTC/USD",
                        ExecutionParameters.Side.BUY, ExecutionParameters.OrderType.MARKET,
                        new BigDecimal("2"), null), new IdempotencyKey(UUID.randomUUID().toString()),
                NOW, NOW.plusSeconds(300));
        ExecutionAttempt attempt = scenario.attempt(intent);
        BrokerOrder order = scenario.order(intent, attempt, "100", "1", "1");

        assertThatThrownBy(() -> scenario.settlement.settle(intent, attempt, order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("EXIT target trade not found");
        assertThat(balances(scenario.account)).isEqualTo(balancesBefore);
        assertThat(scenario.account.getEquity()).isEqualByComparingTo("10000");
        assertThat(scenario.account.getTrades()).isEmpty();
    }

    private Scenario scenario() {
        UUID owner = UUID.randomUUID();
        BrokerAccount broker = BrokerAccount.create(owner, BrokerProvider.KRAKEN, ExecutionMode.PAPER, "cash", NOW);
        Account account = Account.builder().accountId(UUID.randomUUID()).brokerAccountId(broker.id())
                .broker("KRAKEN").name("cash").baseCurrency("USD").settlementModel(SettlementModel.CASH)
                .equity(new BigDecimal("10000")).peakEquity(new BigDecimal("10000"))
                .user(User.builder().userId(owner).build()).build();
        account.addBalance(AccountBalance.builder().asset("USD").amount(new BigDecimal("10000")).build());
        account.addBalance(AccountBalance.builder().asset("BTC").amount(new BigDecimal("3")).build());

        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(brokers.findById(broker.id())).thenReturn(Optional.of(broker));
        when(accounts.findByBrokerAccountId(broker.id())).thenReturn(Optional.of(account));
        when(accounts.save(account)).thenReturn(account);
        when(marketData.findAll()).thenReturn(java.util.List.of(MarketResponse.builder()
                .provider(MarketProvider.KRAKEN).symbol("BTC/USD")
                .productType(InstrumentProductType.LINEAR_PERPETUAL).build()));

        PaperSettlementService settlement = new PaperSettlementService(brokers, accounts,
                new TradingCalculatorServiceImpl(), null, null, marketData);
        return new Scenario(settlement, account, broker, owner);
    }

    private static Map<String, BigDecimal> balances(Account account) {
        return account.getBalances().stream()
                .collect(java.util.stream.Collectors.toMap(AccountBalance::getAsset, AccountBalance::getAmount));
    }

    private record Scenario(PaperSettlementService settlement, Account account,
                            BrokerAccount broker, UUID owner) {
        void settleEntry(TradeType type, String price, String fee) {
            ExecutionIntent intent = entryIntent(type);
            ExecutionAttempt attempt = attempt(intent);
            settlement.settle(intent, attempt, order(intent, attempt, price, "2", fee));
        }

        void settleExit(Trade trade, String price, String fee) {
            ExecutionParameters.Side side = trade.getType() == TradeType.BUY
                    ? ExecutionParameters.Side.SELL : ExecutionParameters.Side.BUY;
            ExecutionIntent intent = ExecutionIntent.createExit(ExecutionIntentId.newId(), owner, broker.id(),
                    trade.getTradeId(), new ExecutionParameters("BTC/USD", side,
                            ExecutionParameters.OrderType.MARKET, trade.getQuantity(), null),
                    new IdempotencyKey(UUID.randomUUID().toString()), NOW, NOW.plusSeconds(300));
            ExecutionAttempt attempt = attempt(intent);
            settlement.settle(intent, attempt,
                    order(intent, attempt, price, trade.getQuantity().toPlainString(), fee));
        }

        ExecutionIntent entryIntent(TradeType type) {
            ExecutionParameters.Side side = type == TradeType.BUY
                    ? ExecutionParameters.Side.BUY : ExecutionParameters.Side.SELL;
            return ExecutionIntent.create(ExecutionIntentId.newId(),
                    new TradePlanReference(UUID.randomUUID(), 1),
                    new RiskApprovalReference(UUID.randomUUID(), RiskApprovalReference.Decision.APPROVED, NOW),
                    new IdempotencyKey(UUID.randomUUID().toString()), owner, broker.id(),
                    new ExecutionParameters("BTC/USD", side, ExecutionParameters.OrderType.MARKET,
                            new BigDecimal("2"), null), NOW, NOW.plusSeconds(300));
        }

        ExecutionAttempt attempt(ExecutionIntent intent) {
            return ExecutionAttempt.create(ExecutionAttemptId.newId(), intent.id(), 1, NOW, null);
        }

        BrokerOrder order(ExecutionIntent intent, ExecutionAttempt attempt,
                          String price, String quantity, String fee) {
            BrokerOrder order = BrokerOrder.acknowledged(BrokerOrderId.newId(), intent.id(),
                    attempt.id(), "SIM", NOW);
            order.addFill(new BrokerOrder.Fill("fill-" + UUID.randomUUID(), new BigDecimal(quantity),
                    new BigDecimal(price), new BigDecimal(fee), NOW), true, NOW);
            return order;
        }
    }
}
