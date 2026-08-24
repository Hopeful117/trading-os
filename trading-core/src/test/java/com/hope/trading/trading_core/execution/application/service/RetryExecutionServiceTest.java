package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.service.ExecuteTradeService;
import com.hope.trading.trading_core.execution.application.port.ExecutionEventPublisher;
import com.hope.trading.trading_core.execution.application.port.ExecutionMetrics;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionAttemptRepositoryPort;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetryExecutionServiceTest {

    private final ExecutionIntentRepositoryPort intents = mock(ExecutionIntentRepositoryPort.class);
    private final ExecutionAttemptRepositoryPort attempts = mock(ExecutionAttemptRepositoryPort.class);
    private final ExecuteTradeService execution = mock(ExecuteTradeService.class);
    private final ExecutionEventPublisher events = mock(ExecutionEventPublisher.class);
    private final ExecutionMetrics metrics = mock(ExecutionMetrics.class);
    private final Instant now = Instant.parse("2026-08-23T10:00:00Z");

    private RetryExecutionService service;

    @BeforeEach
    void setUp() {
        service = new RetryExecutionService(
                intents, attempts, execution, events, metrics,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private ExecutionIntentId id() {
        return ExecutionIntentId.newId();
    }

    private ExecutionIntent rehydratedIntent(ExecutionIntentId intentId, ExecutionStatus status) {
        return ExecutionIntent.rehydrate(
                intentId,
                new TradePlanReference(UUID.randomUUID(), 1),
                new RiskApprovalReference(UUID.randomUUID(),
                        RiskApprovalReference.Decision.APPROVED, now),
                new IdempotencyKey("key-" + UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(),
                new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, new java.math.BigDecimal("1"), null),
                status, null, now, now, now.plusSeconds(3600), 0);
    }

    @Test
    void retryThrowsWhenIntentNotFound() {
        ExecutionIntentId id = id();
        when(intents.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retry(id))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void retryThrowsWhenStatusIsSubmissionOutcomeUnknown() {
        ExecutionIntentId id = id();
        ExecutionIntent intent = rehydratedIntent(id, ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN);
        when(intents.findById(id)).thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.retry(id))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("Reconciliation is required");

        verify(events).publish(argThat(list -> list.stream()
                .anyMatch(e -> e instanceof com.hope.trading.trading_core.execution.domain.event.ExecutionEvent.ExecutionRetryAborted)));
    }

    @Test
    void retryThrowsWhenStatusIsReconciliationInProgress() {
        ExecutionIntentId id = id();
        ExecutionIntent intent = rehydratedIntent(id, ExecutionStatus.RECONCILIATION_IN_PROGRESS);
        when(intents.findById(id)).thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.retry(id))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("Reconciliation is required");
    }

    @Test
    void retryTransitionsFailedToValidatedThenExecutes() {
        ExecutionIntentId id = id();
        ExecutionIntent intent = rehydratedIntent(id, ExecutionStatus.FAILED);
        when(intents.findById(id)).thenReturn(Optional.of(intent));
        when(attempts.findLatestByIntentId(id)).thenReturn(Optional.empty());
        ExecutionIntent result = rehydratedIntent(id, ExecutionStatus.VALIDATED);
        when(execution.execute(id)).thenReturn(result);

        ExecutionIntent returned = service.retry(id);

        assertThat(returned).isNotNull();
        verify(intents).save(intent);
        verify(metrics).retryScheduled();
        verify(execution).execute(id);
    }

    @Test
    void retryOnValidatedIntentSkipsTransitionAndExecutes() {
        ExecutionIntentId id = id();
        ExecutionIntent intent = rehydratedIntent(id, ExecutionStatus.VALIDATED);
        when(intents.findById(id)).thenReturn(Optional.of(intent));
        when(attempts.findLatestByIntentId(id)).thenReturn(Optional.empty());
        when(execution.execute(id)).thenReturn(intent);

        ExecutionIntent returned = service.retry(id);

        verify(intents, never()).save(any());
        verify(execution).execute(id);
    }

    @Test
    void retryThrowsWhenStatusIsCreated() {
        ExecutionIntentId id = id();
        ExecutionIntent intent = rehydratedIntent(id, ExecutionStatus.CREATED);
        when(intents.findById(id)).thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.retry(id))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("not retryable");
    }

    @Test
    void retryThrowsWhenStatusIsCompleted() {
        ExecutionIntentId id = id();
        ExecutionIntent intent = rehydratedIntent(id, ExecutionStatus.COMPLETED);
        when(intents.findById(id)).thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.retry(id))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("not retryable");
    }

    @Test
    void retryThrowsWhenStatusIsCancelled() {
        ExecutionIntentId id = id();
        ExecutionIntent intent = rehydratedIntent(id, ExecutionStatus.CANCELLED);
        when(intents.findById(id)).thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.retry(id))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("not retryable");
    }

    @Test
    void retryUsesIncrementedAttemptNumber() {
        ExecutionIntentId id = id();
        ExecutionIntent intent = rehydratedIntent(id, ExecutionStatus.VALIDATED);
        when(intents.findById(id)).thenReturn(Optional.of(intent));

        var existingAttempt = com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt.create(
                ExecutionAttemptId.newId(), id, 2, now);
        when(attempts.findLatestByIntentId(id)).thenReturn(Optional.of(existingAttempt));
        when(execution.execute(id)).thenReturn(intent);

        service.retry(id);

        verify(events).publish(argThat(list -> list.stream()
                .filter(e -> e instanceof com.hope.trading.trading_core.execution.domain.event.ExecutionEvent.ExecutionRetryScheduled)
                .map(e -> (com.hope.trading.trading_core.execution.domain.event.ExecutionEvent.ExecutionRetryScheduled) e)
                .anyMatch(e -> e.attemptNumber() == 3)));
    }
}
