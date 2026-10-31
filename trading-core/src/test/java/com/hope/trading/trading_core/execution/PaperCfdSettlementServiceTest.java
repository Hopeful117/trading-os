package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.port.TradeExecutionProvenanceStore;
import com.hope.trading.trading_core.execution.application.service.PaperCfdSettlementService;
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
import com.hope.trading.trading_core.market_data.dto.MarketConstraints;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.market_data.helper.MarketProvider;
import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import com.hope.trading.trading_core.market_data.valuation.ReadOnlyMarketValuationPort;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import com.hope.trading.trading_core.valuation.application.CfdValuationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperCfdSettlementServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    @Test
    void persistsSignedLongWithoutChangingInventoryOrNotionalCash() {
        Scenario scenario = scenario();

        UUID tradeId = scenario.settleEntry(ExecutionParameters.Side.BUY, "1.10000", "1000", "0");

        assertThat(tradeId).isNotNull();
        assertThat(scenario.account.getTrades()).singleElement().satisfies(trade -> {
            assertThat(trade.getQuantity()).isEqualByComparingTo("1000");
            assertThat(trade.getProductType()).isEqualTo(InstrumentProductType.CFD);
            assertThat(trade.getContractMultiplier()).isEqualByComparingTo("1");
            assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
        });
        assertThat(amount(scenario.account, "USD")).isEqualByComparingTo("10000");
        assertThat(scenario.account.getRealizedBalance()).isEqualByComparingTo("10000");
    }

    @Test
    void persistsShortAsNegativeExposureAndEntryReplayIsIdempotent() {
        Scenario scenario = scenario();
        ExecutionContext entry = scenario.entry(ExecutionParameters.Side.SELL, "1.10000", "1000", "0");

        UUID first = scenario.settlement.settle(entry.intent, entry.attempt, entry.order);
        UUID replay = scenario.settlement.settle(entry.intent, entry.attempt, entry.order);

        assertThat(replay).isEqualTo(first);
        assertThat(scenario.account.getTrades()).singleElement()
                .extracting(Trade::getQuantity).isEqualTo(new BigDecimal("-1000"));
        assertThat(amount(scenario.account, "USD")).isEqualByComparingTo("10000");
    }

    @Test
    void marksToMarketFromPersistedExposureAndRealizesFullExitOnce() {
        Scenario scenario = scenario();
        scenario.settleEntry(ExecutionParameters.Side.BUY, "1.10000", "1000", "0");
        Trade trade = scenario.account.getTrades().getFirst();
        scenario.marketValuationReturnsMark(trade, "1.10100");

        scenario.settlement.refreshEquity(scenario.account);
        assertThat(scenario.account.getEquity()).isEqualByComparingTo("10001");

        ExecutionContext exit = scenario.exit(trade, ExecutionParameters.Side.SELL,
                "1.10200", "1000", "0");
        UUID first = scenario.settlement.settle(exit.intent, exit.attempt, exit.order);
        UUID replay = scenario.settlement.settle(exit.intent, exit.attempt, exit.order);

        assertThat(replay).isEqualTo(first);
        assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
        assertThat(trade.getPnl()).isEqualByComparingTo("2");
        assertThat(scenario.account.getRealizedBalance()).isEqualByComparingTo("10002");
        assertThat(scenario.account.getEquity()).isEqualByComparingTo("10002");
    }

    @Test
    void realizesShortFullExitWithOpposingDirection() {
        Scenario scenario = scenario();
        scenario.settleEntry(ExecutionParameters.Side.SELL, "1.10000", "1000", "0");
        Trade trade = scenario.account.getTrades().getFirst();

        ExecutionContext exit = scenario.exit(trade, ExecutionParameters.Side.BUY,
                "1.09900", "1000", "0");
        scenario.settlement.settle(exit.intent, exit.attempt, exit.order);

        assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
        assertThat(trade.getPnl()).isEqualByComparingTo("1");
        assertThat(scenario.account.getRealizedBalance()).isEqualByComparingTo("10001");
        assertThat(scenario.account.getEquity()).isEqualByComparingTo("10001");
    }

    @Test
    void rejectsPartialExitAndLiveContextWithoutMutation() {
        Scenario scenario = scenario();
        scenario.settleEntry(ExecutionParameters.Side.BUY, "1.10000", "1000", "0");
        Trade trade = scenario.account.getTrades().getFirst();
        ExecutionContext partial = scenario.exit(trade, ExecutionParameters.Side.SELL,
                "1.10100", "500", "0");

        assertThatThrownBy(() -> scenario.settlement.settle(partial.intent, partial.attempt, partial.order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CFD EXIT fill quantity must equal target quantity");
        assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
        assertThat(scenario.account.getRealizedBalance()).isEqualByComparingTo("10000");
        assertThatThrownBy(() -> new PaperCfdSettlementService(scenario.brokers, scenario.accounts,
                scenario.marketData, scenario.provenance, scenario.valuation,
                Clock.fixed(NOW, ZoneOffset.UTC)).settle(
                scenario.liveEntry().intent, scenario.liveEntry().attempt, scenario.liveEntry().order))
                .isInstanceOf(IllegalStateException.class)
                 .hasMessage("PAPER CFD settlement rejects LIVE context");
    }

    @Test
    void rejectsExitForAnotherInstrument() {
        Scenario scenario = scenario();
        scenario.settleEntry(ExecutionParameters.Side.BUY, "1.10000", "1000", "0");
        Trade trade = scenario.account.getTrades().getFirst();
        ExecutionContext exit = scenario.exit(trade, "GBP/USD", ExecutionParameters.Side.SELL,
                "1.10200", "1000", "0");

        assertThatThrownBy(() -> scenario.settlement.settle(exit.intent, exit.attempt, exit.order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CFD EXIT instrument does not match target trade");
        assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
    }

    @Test
    void rejectsFillQuantityDifferentFromEntryIntent() {
        Scenario scenario = scenario();
        ExecutionContext entry = scenario.entryWithRequestedQuantity(
                ExecutionParameters.Side.BUY, "1.10000", "1000", "500", "0");

        assertThatThrownBy(() -> scenario.settlement.settle(entry.intent, entry.attempt, entry.order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CFD fill quantity does not match execution intent");
        assertThat(scenario.account.getTrades()).isEmpty();
    }

    private Scenario scenario() {
        UUID owner = UUID.randomUUID();
        BrokerAccount broker = BrokerAccount.create(owner, BrokerProvider.KRAKEN, ExecutionMode.PAPER,
                "paper", NOW);
        Account account = Account.builder().accountId(UUID.randomUUID()).brokerAccountId(broker.id())
                .broker("CTRADER").name("paper").baseCurrency("USD").settlementModel(SettlementModel.CASH)
                .equity(new BigDecimal("10000")).peakEquity(new BigDecimal("10000"))
                .user(User.builder().userId(owner).build()).build();
        account.addBalance(AccountBalance.builder().asset("USD").amount(new BigDecimal("10000")).build());

        BrokerAccountRepository brokers = mock(BrokerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        ReadOnlyMarketValuationPort marketValuation = mock(ReadOnlyMarketValuationPort.class);
        InMemoryProvenance provenance = new InMemoryProvenance();
        when(brokers.findById(broker.id())).thenReturn(Optional.of(broker));
        when(accounts.findByBrokerAccountId(broker.id())).thenReturn(Optional.of(account));
        when(accounts.save(account)).thenReturn(account);
        when(marketData.findAll()).thenReturn(List.of(cfdMarket()));
        CfdValuationService valuation = new CfdValuationService(marketValuation,
                Clock.fixed(NOW, ZoneOffset.UTC));
        PaperCfdSettlementService settlement = new PaperCfdSettlementService(brokers, accounts,
                marketData, provenance, valuation, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Scenario(settlement, account, broker, owner, brokers, accounts, marketData,
                provenance, marketValuation, valuation);
    }

    private MarketResponse cfdMarket() {
        return MarketResponse.builder().marketId(UUID.randomUUID()).provider(MarketProvider.CTRADER)
                .symbol("EUR/USD").baseAsset("EUR").quoteAsset("USD")
                .productType(InstrumentProductType.CFD).quantityUnit("UNDERLYING_UNITS")
                .contractMultiplier(BigDecimal.ONE).contractSourceId("ctrader:eurusd")
                .contractSourceVersion(1L).contractObservedAt(NOW.minusSeconds(30))
                .marketConstraints(MarketConstraints.builder().minimumOrderSize(new BigDecimal("1000"))
                        .orderStepSize(new BigDecimal("1000")).quantityPrecision(0).pricePrecision(5).build())
                .build();
    }

    private static BigDecimal amount(Account account, String asset) {
        return account.getBalances().stream().filter(balance -> asset.equals(balance.getAsset()))
                .map(AccountBalance::getAmount).findFirst().orElse(BigDecimal.ZERO);
    }

    private record Scenario(PaperCfdSettlementService settlement, Account account,
                            BrokerAccount broker, UUID owner, BrokerAccountRepository brokers,
                            AccountRepository accounts, MarketDataClient marketData,
                            InMemoryProvenance provenance, ReadOnlyMarketValuationPort marketValuation,
                            CfdValuationService valuation) {
        UUID settleEntry(ExecutionParameters.Side side, String price, String quantity, String fee) {
            ExecutionContext context = entry(side, price, quantity, fee);
            return settlement.settle(context.intent, context.attempt, context.order);
        }

        ExecutionContext entry(ExecutionParameters.Side side, String price, String quantity, String fee) {
            return entryWithRequestedQuantity(side, price, quantity, quantity, fee);
        }

        ExecutionContext entryWithRequestedQuantity(ExecutionParameters.Side side, String price,
                                                    String requestedQuantity, String fillQuantity, String fee) {
            ExecutionIntent intent = ExecutionIntent.create(ExecutionIntentId.newId(),
                    new TradePlanReference(UUID.randomUUID(), 1),
                    new RiskApprovalReference(UUID.randomUUID(), RiskApprovalReference.Decision.APPROVED, NOW),
                    new IdempotencyKey(UUID.randomUUID().toString()), owner, broker.id(),
                    new ExecutionParameters("EUR/USD", side, ExecutionParameters.OrderType.MARKET,
                            new BigDecimal(requestedQuantity), null), NOW, NOW.plusSeconds(300));
            return context(intent, price, fillQuantity, fee);
        }

        ExecutionContext exit(Trade trade, ExecutionParameters.Side side, String price,
                              String quantity, String fee) {
            return exit(trade, "EUR/USD", side, price, quantity, fee);
        }

        ExecutionContext exit(Trade trade, String instrument, ExecutionParameters.Side side, String price,
                              String quantity, String fee) {
            ExecutionIntent intent = ExecutionIntent.createExit(ExecutionIntentId.newId(), owner,
                    broker.id(), trade.getTradeId(), new ExecutionParameters(instrument, side,
                            ExecutionParameters.OrderType.MARKET, new BigDecimal(quantity), null),
                    new IdempotencyKey(UUID.randomUUID().toString()), NOW, NOW.plusSeconds(300));
            return context(intent, price, quantity, fee);
        }

        ExecutionContext liveEntry() {
            BrokerAccount live = BrokerAccount.create(owner, BrokerProvider.KRAKEN, ExecutionMode.LIVE,
                    "live", NOW);
            when(brokers.findById(live.id())).thenReturn(Optional.of(live));
            ExecutionIntent intent = ExecutionIntent.create(ExecutionIntentId.newId(),
                    new TradePlanReference(UUID.randomUUID(), 1),
                    new RiskApprovalReference(UUID.randomUUID(), RiskApprovalReference.Decision.APPROVED, NOW),
                    new IdempotencyKey(UUID.randomUUID().toString()), owner, live.id(),
                    new ExecutionParameters("EUR/USD", ExecutionParameters.Side.BUY,
                            ExecutionParameters.OrderType.MARKET, new BigDecimal("1000"), null), NOW,
                    NOW.plusSeconds(300));
            return context(intent, "1.10000", "1000", "0");
        }

        void marketValuationReturnsMark(Trade trade, String mark) {
            MarketValuationPort.Fact fact = new MarketValuationPort.Fact("INSTRUMENT",
                    trade.getTradeId().toString(), UUID.randomUUID(), null,
                    trade.getQuantity().signum() > 0 ? MarketValuationPort.PriceUse.CONSERVATIVE_SELL
                            : MarketValuationPort.PriceUse.CONSERVATIVE_BUY,
                    new BigDecimal(mark), new BigDecimal(mark), null, "AVAILABLE", "mark-source",
                    NOW.minusSeconds(5), NOW.minusSeconds(4), null, null, List.of());
            when(marketValuation.valueReadOnly(any(), any(), any(), any(), any()))
                    .thenReturn(new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "USD", NOW, NOW,
                            "policy-v1", "PT5M", true, List.of(fact), "facts", List.of()));
        }

        private ExecutionContext context(ExecutionIntent intent, String price, String quantity, String fee) {
            ExecutionAttempt attempt = ExecutionAttempt.create(ExecutionAttemptId.newId(), intent.id(), 1, NOW, null);
            BrokerOrder order = BrokerOrder.acknowledged(BrokerOrderId.newId(), intent.id(), attempt.id(),
                    "SIM", NOW);
            order.addFill(new BrokerOrder.Fill("fill-" + UUID.randomUUID(), new BigDecimal(quantity),
                    new BigDecimal(price), new BigDecimal(fee), NOW), true, NOW);
            return new ExecutionContext(intent, attempt, order);
        }
    }

    private record ExecutionContext(ExecutionIntent intent, ExecutionAttempt attempt, BrokerOrder order) { }

    private static final class InMemoryProvenance implements TradeExecutionProvenanceStore {
        private final List<Provenance> values = new ArrayList<>();

        @Override
        public Optional<Provenance> findByBrokerOrderIdAndFillId(UUID brokerOrderId, String fillId) {
            return values.stream().filter(value -> value.brokerOrderId().equals(brokerOrderId)
                    && value.fillId().equals(fillId)).findFirst();
        }

        @Override
        public List<Provenance> findAllByTradeIdsAndExecutedAtBetween(List<UUID> tradeIds,
                                                                       Instant start, Instant end) {
            return values.stream().filter(value -> tradeIds.contains(value.tradeId())).toList();
        }

        @Override
        public void save(Provenance provenance) {
            values.add(provenance);
        }
    }
}
