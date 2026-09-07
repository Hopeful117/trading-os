package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.exception.ExecutionExpiredException;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetryT1ExecutionServiceTest {

    private final ExecutionIntentRepositoryPort intents = mock(ExecutionIntentRepositoryPort.class);
    private final ExecuteTradeService execution = mock(ExecuteTradeService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);

    private RetryT1ExecutionService service;

    @BeforeEach
    void setUp() {
        service = new RetryT1ExecutionService(intents, execution, clock);
    }

    @Test
    void retryThrowsWhenIntentNotFound() {
        UUID id = UUID.randomUUID();
        when(intents.findById(new ExecutionIntentId(id))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retry(new ExecutionIntentId(id)))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void retryThrowsWhenStatusIsNotUnavailable() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        ExecutionIntent intent = ExecutionIntent.rehydrate(
                ExecutionIntentId.newId(),
                new TradePlanReference(UUID.randomUUID(), 1),
                new RiskApprovalReference(UUID.randomUUID(), RiskApprovalReference.Decision.APPROVED, now),
                new IdempotencyKey("key"),
                UUID.randomUUID(), UUID.randomUUID(),
                new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, new java.math.BigDecimal("1"), null),
                ExecutionStatus.VALIDATED, null, now, now, now.plusSeconds(3600), 0
        );
        when(intents.findById(intent.id())).thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.retry(intent.id()))
                .isInstanceOf(InvalidExecutionStateException.class)
                .hasMessageContaining("retry-eligible state");
    }

    @Test
    void retryThrowsWhenExpired() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        Instant created = now.minusSeconds(3600); // created 1 hour ago
        Instant expired = now.minusSeconds(1); // expired 1 second ago
        ExecutionIntent intent = ExecutionIntent.rehydrate(
                ExecutionIntentId.newId(),
                new TradePlanReference(UUID.randomUUID(), 1),
                new RiskApprovalReference(UUID.randomUUID(), RiskApprovalReference.Decision.APPROVED, created),
                new IdempotencyKey("key"),
                UUID.randomUUID(), UUID.randomUUID(),
                new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, new java.math.BigDecimal("1"), null),
                ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE, null, created, now, expired, 0
        );
        when(intents.findById(intent.id())).thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.retry(intent.id()))
                .isInstanceOf(ExecutionExpiredException.class);
    }

    @Test
    void retryDelegatesToExecuteTradeService() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        ExecutionIntent intent = ExecutionIntent.rehydrate(
                ExecutionIntentId.newId(),
                new TradePlanReference(UUID.randomUUID(), 1),
                new RiskApprovalReference(UUID.randomUUID(), RiskApprovalReference.Decision.APPROVED, now),
                new IdempotencyKey("key"),
                UUID.randomUUID(), UUID.randomUUID(),
                new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, new java.math.BigDecimal("1"), null),
                ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE, null, now, now, now.plusSeconds(3600), 0
        );
        ExecutionIntent result = ExecutionIntent.rehydrate(
                intent.id(),
                intent.tradePlan(),
                intent.riskApproval(),
                intent.idempotencyKey(),
                intent.initiatorId(),
                intent.brokerAccountId(),
                intent.parameters(),
                ExecutionStatus.COMPLETED, null, now, now, intent.expiresAt(), 1
        );
        when(intents.findById(intent.id())).thenReturn(Optional.of(intent));
        when(execution.execute(intent.id())).thenReturn(result);

        ExecutionIntent returned = service.retry(intent.id());

        assertThat(returned).isEqualTo(result);
        verify(execution).execute(intent.id());
    }
}