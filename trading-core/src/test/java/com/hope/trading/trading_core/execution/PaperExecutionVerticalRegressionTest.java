package com.hope.trading.trading_core.execution;

import com.hope.trading.risk.domain.RiskTypes.RiskDecision;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.pipeline.BrokerResponseProcessingStep;
import com.hope.trading.trading_core.execution.application.pipeline.BrokerSubmissionStep;
import com.hope.trading.trading_core.execution.application.pipeline.ExecutionAttemptCreationStep;
import com.hope.trading.trading_core.execution.application.pipeline.ExecutionFinalizationStep;
import com.hope.trading.trading_core.execution.application.pipeline.ExecutionValidationStep;
import com.hope.trading.trading_core.execution.application.pipeline.IdempotencyVerificationStep;
import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.application.service.ExecuteTradeService;
import com.hope.trading.trading_core.execution.application.service.ExecutionTimeRiskRevalidationService;
import com.hope.trading.trading_core.execution.application.service.PaperSettlementService;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference;
import com.hope.trading.trading_core.execution.domain.model.TradePlanReference;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.service.ExecutionValidationService;
import com.hope.trading.trading_core.execution.domain.service.IdempotencyService;
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.execution.infrastructure.adapter.BrokerExecutionAdapter;
import com.hope.trading.trading_core.execution.infrastructure.adapter.BrokerExecutionClient;
import com.hope.trading.trading_core.execution.infrastructure.adapter.RoutingBrokerExecutionAdapter;
import com.hope.trading.trading_core.execution.infrastructure.adapter.SimulatedExecutionAdapter;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotDto;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotRequest;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.repository.AccountRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaperExecutionVerticalRegressionTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void paperExecutionRoutesToSimulationFillsSettlesAndFinalizesForBuyAndSell() {
        verifyScenario(ExecutionParameters.Side.BUY, "BTC/USD", "0.1",
                "50000", "50100", "10000", null,
                "50100", "4990", "0.1", "10000");

        verifyScenario(ExecutionParameters.Side.SELL, "ETH/USD", "1",
                "2995", "3005", "12995", "1",
                "2995", "12995", "0", "12995");
    }

    private void verifyScenario(ExecutionParameters.Side side, String instrument, String quantity,
                                String bid, String ask, String initialEquity,
                                String initialBaseBalance, String expectedFillPrice,
                                String expectedUsdBalance, String expectedBaseBalance,
                                String expectedEquity) {
        UUID ownerId = UUID.randomUUID();
        BrokerAccount paperBrokerAccount = BrokerAccount.create(
                ownerId, BrokerProvider.KRAKEN, ExecutionMode.PAPER, "Paper Account", NOW);
        BrokerAccountRepository brokerAccounts = mock(BrokerAccountRepository.class);
        when(brokerAccounts.findById(paperBrokerAccount.id()))
                .thenReturn(Optional.of(paperBrokerAccount));

        Account account = Account.builder()
                .accountId(UUID.randomUUID())
                .user(User.builder().userId(ownerId).build())
                .broker(BrokerProvider.KRAKEN.name())
                .name("Paper Account")
                .baseCurrency("USD")
                .equity(decimal(initialEquity))
                .peakEquity(decimal(initialEquity))
                .build();
        account.addBalance(balance("USD", new BigDecimal("10000")));
        String baseAsset = instrument.substring(0, instrument.indexOf('/'));
        if (initialBaseBalance != null) {
            account.addBalance(balance(baseAsset, decimal(initialBaseBalance)));
        }

        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findByBrokerAccountId(paperBrokerAccount.id()))
                .thenReturn(Optional.of(account));
        when(accounts.save(account)).thenReturn(account);

        UUID marketId = UUID.randomUUID();
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(marketData.findAll()).thenReturn(List.of(MarketResponse.builder()
                .marketId(marketId)
                .symbol(instrument)
                .baseAsset(baseAsset)
                .quoteAsset("USD")
                .build()));
        when(marketData.findPriceSnapshots(any(MarketPriceSnapshotRequest.class)))
                .thenReturn(List.of(new MarketPriceSnapshotDto(
                        marketId, instrument, decimal(expectedFillPrice), decimal(bid), decimal(ask),
                        true, NOW, MarketPriceSnapshotStatus.FRESH)));

        BrokerExecutionClient liveClient = mock(BrokerExecutionClient.class);
        CapturingSimulatedExecutionAdapter simulated = new CapturingSimulatedExecutionAdapter(
                brokerAccounts, marketData);
        RoutingBrokerExecutionAdapter routing = new RoutingBrokerExecutionAdapter(
                new BrokerExecutionAdapter(liveClient), simulated, brokerAccounts);

        var intents = new ExecutionTestSupport.Intents();
        var attempts = new ExecutionTestSupport.Attempts();
        var orders = new ExecutionTestSupport.Orders();
        var ids = new ExecutionTestSupport.Ids();
        var events = new ExecutionTestSupport.Events();
        var metrics = new ExecutionTestSupport.Metrics();
        var lifecycle = new ExecutionLifecycleService();
        PaperSettlementService settlement = spy(new PaperSettlementService(brokerAccounts, accounts));
        ExecutionTimeRiskRevalidationService t1 = mock(ExecutionTimeRiskRevalidationService.class);
        when(t1.evaluateAndPersist(any(), any())).thenReturn(
                new ExecutionTimeRiskRevalidationService.T1Outcome(
                        UUID.randomUUID(), RiskDecision.APPROVED, null, true));

        ExecutionIntent intent = ExecutionIntent.create(
                ExecutionIntentId.newId(),
                new TradePlanReference(UUID.randomUUID(), 1),
                new RiskApprovalReference(UUID.randomUUID(),
                        RiskApprovalReference.Decision.APPROVED, NOW.minusSeconds(1)),
                new IdempotencyKey("paper-" + side.name().toLowerCase() + "-vertical"),
                ownerId,
                paperBrokerAccount.id(),
                new ExecutionParameters(instrument, side, ExecutionParameters.OrderType.MARKET,
                        decimal(quantity), null),
                NOW.minusSeconds(10),
                NOW.plusSeconds(600));
        intents.save(intent);

        ExecuteTradeService execution = new ExecuteTradeService(
                intents,
                new ExecutionValidationStep(new ExecutionValidationService(), lifecycle),
                new IdempotencyVerificationStep(new IdempotencyService()),
                new ExecutionAttemptCreationStep(attempts, ids),
                new BrokerSubmissionStep(routing, intents, attempts, lifecycle),
                new BrokerResponseProcessingStep(ids),
                new ExecutionFinalizationStep(
                        intents, attempts, orders, lifecycle, metrics, settlement, brokerAccounts),
                events,
                CLOCK,
                t1);

        ExecutionIntent result = execution.execute(intent.id());

        assertThat(paperBrokerAccount.executionMode()).isEqualTo(ExecutionMode.PAPER);
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(simulated.submissions).isOne();
        assertThat(simulated.request.brokerAccountId()).isEqualTo(paperBrokerAccount.id());
        assertThat(simulated.request.parameters().side()).isEqualTo(side);
        assertThat(simulated.acknowledgement).isNotNull();
        assertThat(simulated.acknowledgement.externalOrderId()).startsWith("SIM-");
        assertThat(simulated.acknowledgement.fillPrice())
                .isEqualByComparingTo(expectedFillPrice);
        verifyNoInteractions(liveClient);
        verify(marketData).findAll();
        verify(marketData).findPriceSnapshots(any(MarketPriceSnapshotRequest.class));

        BrokerOrder order = orders.findByIntentId(intent.id()).orElseThrow();
        assertThat(order.status()).isEqualTo(BrokerOrderStatus.FILLED);
        assertThat(order.fills()).singleElement().satisfies(fill -> {
            assertThat(fill.price()).isEqualByComparingTo(expectedFillPrice);
            assertThat(fill.quantity()).isEqualByComparingTo(quantity);
            assertThat(fill.fee()).isEqualByComparingTo(BigDecimal.ZERO);
        });
        verify(settlement, times(1)).settle(same(intent), any(), same(order));
        verify(accounts).save(same(account));

        assertThat(amount(account, "USD")).isEqualByComparingTo(expectedUsdBalance);
        assertThat(amount(account, baseAsset)).isEqualByComparingTo(expectedBaseBalance);
        assertThat(account.getEquity()).isEqualByComparingTo(expectedEquity);
        assertThat(account.getPeakEquity()).isEqualByComparingTo(initialEquity);
        assertThat(account.getTrades()).singleElement().satisfies(trade -> {
            assertThat(trade.getSymbol()).isEqualTo(instrument);
            assertThat(trade.getType()).isEqualTo(
                    side == ExecutionParameters.Side.BUY ? TradeType.BUY : TradeType.SELL);
            assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
            assertThat(trade.getQuantity()).isEqualByComparingTo(quantity);
            assertThat(trade.getEntryPrice()).isEqualByComparingTo(expectedFillPrice);
            assertThat(trade.getCurrentPrice()).isEqualByComparingTo(expectedFillPrice);
            assertThat(trade.getOpenedAt()).isEqualTo(NOW);
            assertThat(trade.getAccount()).isSameAs(account);
        });

        assertThatThrownBy(() -> execution.execute(intent.id()))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessage("Terminal execution cannot be submitted");
        verify(settlement, times(1)).settle(same(intent), any(), same(order));
    }

    private static AccountBalance balance(String asset, BigDecimal amount) {
        return AccountBalance.builder().asset(asset).amount(amount).build();
    }

    private static BigDecimal amount(Account account, String asset) {
        return account.getBalances().stream()
                .filter(balance -> asset.equals(balance.getAsset()))
                .map(AccountBalance::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static final class CapturingSimulatedExecutionAdapter extends SimulatedExecutionAdapter {
        private int submissions;
        private BrokerExecutionPort.ExecutionRequest request;
        private BrokerExecutionPort.Acknowledged acknowledgement;

        private CapturingSimulatedExecutionAdapter(BrokerAccountRepository brokerAccounts,
                                                    MarketDataClient marketData) {
            super(brokerAccounts, marketData);
        }

        @Override
        public SubmissionResult submit(ExecutionRequest request) {
            submissions++;
            this.request = request;
            SubmissionResult result = super.submit(request);
            if (result instanceof BrokerExecutionPort.Acknowledged acknowledged) {
                acknowledgement = acknowledged;
            }
            return result;
        }
    }
}
