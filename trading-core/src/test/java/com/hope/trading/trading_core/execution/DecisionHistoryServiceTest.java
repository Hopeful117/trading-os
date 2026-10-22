package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.service.DecisionHistoryService;
import com.hope.trading.trading_core.execution.application.service.QueryExecutionService;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.aggregate.TradeOutcome;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.repository.TradeOutcomeRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.execution.domain.valueobject.TradeOutcomeStatus;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.repository.AccountRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecisionHistoryServiceTest {
    @Test
    void hidesForeignTradeLineageInsteadOfExposingItAsAccountHistory() {
        UUID accountId = UUID.randomUUID();
        UUID foreignTradeId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Account account = Account.builder().accountId(accountId).trades(List.of(
                Trade.builder().tradeId(UUID.randomUUID()).symbol("BTC/USD").build())).build();
        TradeOutcome outcome = TradeOutcome.rehydrate(
                UUID.randomUUID(), accountId, UUID.randomUUID(), executionId, foreignTradeId,
                UUID.randomUUID(), 1, List.of(), "BTC/USD", ExecutionParameters.Side.BUY,
                BigDecimal.ONE, BigDecimal.ONE, null, List.of(), null, null, null, null, null,
                TradeOutcomeStatus.ENTRY_ACKNOWLEDGED, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));

        TradeOutcomeRepositoryPort outcomes = mock(TradeOutcomeRepositoryPort.class);
        AccountRepository accounts = mock(AccountRepository.class);
        ExecutionIntentRepositoryPort intents = mock(ExecutionIntentRepositoryPort.class);
        TradePlanRiskPort tradePlans = mock(TradePlanRiskPort.class);
        when(accounts.findById(accountId)).thenReturn(java.util.Optional.of(account));
        when(outcomes.findByAccountId(accountId)).thenReturn(List.of(outcome));
        when(tradePlans.load(outcome.tradePlanId(), outcome.tradePlanVersion()))
                .thenThrow(new RuntimeException("unavailable"));

        DecisionHistoryService service = new DecisionHistoryService(
                outcomes, accounts, new QueryExecutionService(intents), tradePlans);

        var result = service.findByAccount(accountId).getFirst();

        assertThat(result.origin()).isEqualTo("UNAVAILABLE");
        assertThat(result.tradeId()).isNull();
        assertThat(result.lineageAvailable()).isFalse();
        assertThat(result.resultAvailable()).isFalse();
        assertThat(result.realizedPnl()).isNull();
        assertThat(result.closeExecutionIntentId()).isNull();
    }

    @Test
    void exposesManualOriginAndDeterministicCloseExecution() {
        UUID accountId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID closeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Account account = Account.builder().accountId(accountId).user(
                com.hope.trading.trading_core.model.User.builder().userId(ownerId).build()).trades(List.of(
                Trade.builder().tradeId(tradeId).symbol("BTC/USD").build())).build();
        TradeOutcome outcome = TradeOutcome.rehydrate(
                UUID.randomUUID(), accountId, brokerAccountId, executionId, tradeId,
                UUID.randomUUID(), 1, List.of(), "BTC/USD", ExecutionParameters.Side.BUY,
                BigDecimal.ONE, BigDecimal.ONE, null, List.of(), null, null, null, null, null,
                TradeOutcomeStatus.ENTRY_ACKNOWLEDGED, now, now);
        ExecutionIntent close = ExecutionIntent.createExit(
                new com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId(closeId),
                ownerId, brokerAccountId, accountId, tradeId,
                new ExecutionParameters("BTC/USD", ExecutionParameters.Side.SELL,
                        ExecutionParameters.OrderType.MARKET, BigDecimal.ONE, null),
                new IdempotencyKey("close-key"), now, now.plusSeconds(300));

        TradeOutcomeRepositoryPort outcomes = mock(TradeOutcomeRepositoryPort.class);
        AccountRepository accounts = mock(AccountRepository.class);
        ExecutionIntentRepositoryPort intents = mock(ExecutionIntentRepositoryPort.class);
        TradePlanRiskPort tradePlans = mock(TradePlanRiskPort.class);
        when(accounts.findById(accountId)).thenReturn(java.util.Optional.of(account));
        when(outcomes.findByAccountId(accountId)).thenReturn(List.of(outcome));
        when(intents.findAll()).thenReturn(List.of(close));
        var plan = mock(TradePlanRiskPort.Snapshot.class);
        when(plan.origin()).thenReturn("MANUAL");
        when(tradePlans.load(outcome.tradePlanId(), outcome.tradePlanVersion())).thenReturn(plan);

        var result = new DecisionHistoryService(outcomes, accounts, new QueryExecutionService(intents), tradePlans)
                .findByAccount(accountId).getFirst();

        assertThat(result.origin()).isEqualTo("MANUAL");
        assertThat(result.closeExecutionIntentId()).isEqualTo(closeId);
        assertThat(result.closeExecutionStatus()).isEqualTo(close.status());
    }
}
