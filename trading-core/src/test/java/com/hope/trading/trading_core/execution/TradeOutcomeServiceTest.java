package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.service.TradeOutcomeService;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.repository.TradeOutcomeRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.shared.domain.model.TradePlanProvenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TradeOutcomeServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    @Mock TradeOutcomeRepositoryPort repository;

    @Test
    void createsImmutableEntrySnapshotWithAverageFillAndProvenance() {
        UUID intentId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        TradePlanProvenance provenance = new TradePlanProvenance(
                UUID.randomUUID(), 2, UUID.randomUUID(), UUID.randomUUID(), 3);
        ExecutionIntent intent = ExecutionIntent.create(new ExecutionIntentId(intentId),
                new TradePlanReference(planId, 4),
                new RiskApprovalReference(UUID.randomUUID(), RiskApprovalReference.Decision.APPROVED, NOW),
                new IdempotencyKey("outcome-test"), UUID.randomUUID(), UUID.randomUUID(), accountId,
                new ExecutionParameters("BTCUSD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, new BigDecimal("2"), null),
                List.of(provenance), NOW, NOW.plusSeconds(600));
        ExecutionAttempt attempt = ExecutionAttempt.create(new ExecutionAttemptId(UUID.randomUUID()),
                intent.id(), 1, NOW, UUID.randomUUID());
        BrokerOrder order = BrokerOrder.acknowledged(new BrokerOrderId(UUID.randomUUID()), intent.id(),
                attempt.id(), "broker-order", NOW);
        order.addFill(new BrokerOrder.Fill("fill-1", new BigDecimal("1"), new BigDecimal("100"),
                BigDecimal.ONE, NOW), false, NOW);
        order.addFill(new BrokerOrder.Fill("fill-2", new BigDecimal("1"), new BigDecimal("110"),
                BigDecimal.ONE, NOW), true, NOW);
        when(repository.findByExecutionIntentId(intentId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new TradeOutcomeService(repository, () -> UUID.randomUUID())
                .acknowledge(intent, order, NOW);

        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.entryPrice()).isEqualByComparingTo("105");
        assertThat(result.provenance()).containsExactly(provenance);
        verify(repository).save(any());
    }

    @Test
    void doesNotCreateDuplicateOutcomeWhenFinalizationIsRetried() {
        ExecutionIntent intent = ExecutionTestSupport.intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(new ExecutionAttemptId(UUID.randomUUID()),
                intent.id(), 1, NOW, UUID.randomUUID());
        BrokerOrder order = BrokerOrder.acknowledged(new BrokerOrderId(UUID.randomUUID()), intent.id(),
                attempt.id(), "broker-order", NOW);
        var existing = com.hope.trading.trading_core.execution.domain.aggregate.TradeOutcome.acknowledged(
                UUID.randomUUID(), intent.initiatorId(), intent.brokerAccountId(), intent.id().value(),
                intent.tradePlan().tradePlanId(), intent.tradePlan().version(), List.of(), intent.parameters(), null, NOW);
        when(repository.findByExecutionIntentId(intent.id().value())).thenReturn(Optional.of(existing));

        var result = new TradeOutcomeService(repository, () -> UUID.randomUUID())
                .acknowledge(intent, order, NOW);

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void filtersOutcomesByStrategyInstrumentAndStatus() {
        UUID strategyId = UUID.randomUUID();
        var outcome = com.hope.trading.trading_core.execution.domain.aggregate.TradeOutcome.acknowledged(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                List.of(new TradePlanProvenance(UUID.randomUUID(), 1, UUID.randomUUID(), strategyId, 1)),
                new ExecutionParameters("BTCUSD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, BigDecimal.ONE, null), null, NOW);
        when(repository.findByAccountId(outcome.accountId())).thenReturn(List.of(outcome));

        var result = new TradeOutcomeService(repository, UUID::randomUUID).findByAccount(
                outcome.accountId(), strategyId, "btcusd", TradeOutcomeStatus.ENTRY_ACKNOWLEDGED,
                NOW.minusSeconds(1), NOW.plusSeconds(1));

        assertThat(result).containsExactly(outcome);
    }

    @Test
    void reloadsExistingOutcomeWhenConcurrentInsertWins() {
        ExecutionIntent intent = ExecutionTestSupport.intent(ExecutionStatus.VALIDATED);
        ExecutionAttempt attempt = ExecutionAttempt.create(new ExecutionAttemptId(UUID.randomUUID()),
                intent.id(), 1, NOW, UUID.randomUUID());
        BrokerOrder order = BrokerOrder.acknowledged(new BrokerOrderId(UUID.randomUUID()), intent.id(),
                attempt.id(), "broker-order", NOW);
        var existing = com.hope.trading.trading_core.execution.domain.aggregate.TradeOutcome.acknowledged(
                UUID.randomUUID(), intent.initiatorId(), intent.brokerAccountId(), intent.id().value(),
                intent.tradePlan().tradePlanId(), intent.tradePlan().version(), List.of(), intent.parameters(), null, NOW);
        when(repository.findByExecutionIntentId(intent.id().value()))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("concurrent insert"));

        var result = new TradeOutcomeService(repository, UUID::randomUUID)
                .acknowledge(intent, order, NOW);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void rejectsAcknowledgementWhenAccountIdentityIsUnavailable() {
        ExecutionIntent intent = ExecutionIntent.create(new ExecutionIntentId(UUID.randomUUID()),
                new TradePlanReference(UUID.randomUUID(), 1),
                new RiskApprovalReference(UUID.randomUUID(), RiskApprovalReference.Decision.APPROVED, NOW),
                new IdempotencyKey("missing-account"), UUID.randomUUID(), UUID.randomUUID(), null,
                new ExecutionParameters("BTCUSD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, BigDecimal.ONE, null),
                List.of(), NOW, NOW.plusSeconds(600));
        ExecutionAttempt attempt = ExecutionAttempt.create(new ExecutionAttemptId(UUID.randomUUID()),
                intent.id(), 1, NOW, UUID.randomUUID());
        BrokerOrder order = BrokerOrder.acknowledged(new BrokerOrderId(UUID.randomUUID()), intent.id(),
                attempt.id(), "broker-order", NOW);

        when(repository.findByExecutionIntentId(intent.id().value())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new TradeOutcomeService(repository, UUID::randomUUID)
                .acknowledge(intent, order, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot create TradeOutcome without account identity");
        verify(repository, never()).save(any());
    }
}
