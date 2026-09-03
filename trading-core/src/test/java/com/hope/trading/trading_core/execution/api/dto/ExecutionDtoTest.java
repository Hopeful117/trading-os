package com.hope.trading.trading_core.execution.api.dto;

import com.hope.trading.trading_core.execution.domain.aggregate.*;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionDtoTest {

    @Test
    void fromMapsAllFieldsFromExecutionIntent() {
        UUID planId = UUID.randomUUID();
        UUID evalId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        UUID initiatorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        Instant expiresAt = now.plusSeconds(3600);

        ExecutionIntent intent = ExecutionIntent.create(
                ExecutionIntentId.newId(),
                new TradePlanReference(planId, 1),
                new RiskApprovalReference(evalId, RiskApprovalReference.Decision.APPROVED, now),
                new IdempotencyKey("idem-key-1"),
                initiatorId,
                brokerAccountId,
                new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, new BigDecimal("0.1"), null),
                now,
                expiresAt
        );

        ExecutionDto dto = ExecutionDto.from(intent);

        assertThat(dto.id()).isEqualTo(intent.id().value());
        assertThat(dto.tradePlanId()).isEqualTo(planId);
        assertThat(dto.tradePlanVersion()).isEqualTo(1);
        assertThat(dto.riskEvaluationId()).isEqualTo(evalId);
        assertThat(dto.idempotencyKey()).isEqualTo("idem-key-1");
        assertThat(dto.brokerAccountId()).isEqualTo(brokerAccountId);
        assertThat(dto.status()).isEqualTo(ExecutionStatus.CREATED);
        assertThat(dto.createdAt()).isEqualTo(now);
        assertThat(dto.updatedAt()).isEqualTo(now);
        assertThat(dto.expiresAt()).isEqualTo(expiresAt);
        assertThat(dto.version()).isEqualTo(0);
        assertThat(dto.brokerExternalOrderId()).isNull();
        assertThat(dto.brokerOrderStatus()).isNull();
        assertThat(dto.filledQuantity()).isNull();
        assertThat(dto.averageFillPrice()).isNull();
        assertThat(dto.totalFees()).isNull();
        assertThat(dto.failureReason()).isNull();
    }

    @Test
    void enrichedDtoIncludesBrokerOrderWhenPresent() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        ExecutionIntent intent = createIntent(now);
        ExecutionAttempt attempt = ExecutionAttempt.rehydrate(
                ExecutionAttemptId.newId(), intent.id(), 1,
                AttemptStatus.SUCCEEDED, "corr-1", "ACKNOWLEDGED",
                now, now, now, 0);
        BrokerOrder order = BrokerOrder.rehydrate(
                BrokerOrderId.newId(), intent.id(), attempt.id(),
                "ext-order-1", BrokerOrderStatus.ACKNOWLEDGED,
                List.of(), now, now, 0);

        ExecutionDto dto = ExecutionDto.from(intent, Optional.of(order), Optional.of(attempt));

        assertThat(dto.brokerExternalOrderId()).isEqualTo("ext-order-1");
        assertThat(dto.brokerOrderStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(dto.filledQuantity()).isNull();
        assertThat(dto.averageFillPrice()).isNull();
        assertThat(dto.totalFees()).isNull();
    }

    @Test
    void enrichedDtoComputesFillAggregation() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        ExecutionIntent intent = createIntent(now);
        ExecutionAttempt attempt = ExecutionAttempt.rehydrate(
                ExecutionAttemptId.newId(), intent.id(), 1,
                AttemptStatus.SUCCEEDED, "corr-1", "ACKNOWLEDGED",
                now, now, now, 0);
        List<BrokerOrder.Fill> fills = List.of(
                new BrokerOrder.Fill("f1", new BigDecimal("0.5"), new BigDecimal("50000"), new BigDecimal("5"), now),
                new BrokerOrder.Fill("f2", new BigDecimal("0.3"), new BigDecimal("50100"), new BigDecimal("3"), now)
        );
        BrokerOrder order = BrokerOrder.rehydrate(
                BrokerOrderId.newId(), intent.id(), attempt.id(),
                "ext-order-1", BrokerOrderStatus.PARTIALLY_FILLED,
                fills, now, now, 0);

        ExecutionDto dto = ExecutionDto.from(intent, Optional.of(order), Optional.of(attempt));

        assertThat(dto.filledQuantity()).isEqualByComparingTo(new BigDecimal("0.8"));
        assertThat(dto.averageFillPrice()).isEqualByComparingTo(new BigDecimal("50037.500000000000"));
        assertThat(dto.totalFees()).isEqualByComparingTo(new BigDecimal("8"));
    }

    @Test
    void enrichedDtoReturnsNullWhenNoBrokerOrder() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        ExecutionIntent intent = createIntent(now);

        ExecutionDto dto = ExecutionDto.from(intent, Optional.empty(), Optional.empty());

        assertThat(dto.brokerExternalOrderId()).isNull();
        assertThat(dto.brokerOrderStatus()).isNull();
        assertThat(dto.filledQuantity()).isNull();
        assertThat(dto.averageFillPrice()).isNull();
        assertThat(dto.totalFees()).isNull();
    }

    @Test
    void enrichedDtoMapsFailureReasonForRejected() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        ExecutionIntent intent = createIntent(now);
        ExecutionAttempt attempt = ExecutionAttempt.rehydrate(
                ExecutionAttemptId.newId(), intent.id(), 1,
                AttemptStatus.FAILED, null, "REJECTED",
                now, now, now, 0);

        ExecutionDto dto = ExecutionDto.from(intent, Optional.empty(), Optional.of(attempt));

        assertThat(dto.failureReason()).isEqualTo("Order rejected by broker");
    }

    @Test
    void enrichedDtoMapsFailureReasonForTimeout() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        ExecutionIntent intent = createIntent(now);
        ExecutionAttempt attempt = ExecutionAttempt.rehydrate(
                ExecutionAttemptId.newId(), intent.id(), 1,
                AttemptStatus.TIMED_OUT, null, "TIMEOUT",
                now, now, now, 0);

        ExecutionDto dto = ExecutionDto.from(intent, Optional.empty(), Optional.of(attempt));

        assertThat(dto.failureReason()).isEqualTo("Submission timed out");
    }

    @Test
    void enrichedDtoMapsFailureReasonForUnknown() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        ExecutionIntent intent = createIntent(now);
        ExecutionAttempt attempt = ExecutionAttempt.rehydrate(
                ExecutionAttemptId.newId(), intent.id(), 1,
                AttemptStatus.OUTCOME_UNKNOWN, null, "OUTCOME_UNKNOWN",
                now, now, now, 0);

        ExecutionDto dto = ExecutionDto.from(intent, Optional.empty(), Optional.of(attempt));

        assertThat(dto.failureReason()).isEqualTo("Submission outcome uncertain");
    }

    @Test
    void enrichedDtoReturnsNullFailureReasonForSuccessfulAttempt() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        ExecutionIntent intent = createIntent(now);
        ExecutionAttempt attempt = ExecutionAttempt.rehydrate(
                ExecutionAttemptId.newId(), intent.id(), 1,
                AttemptStatus.SUCCEEDED, "corr-1", "ACKNOWLEDGED",
                now, now, now, 0);

        ExecutionDto dto = ExecutionDto.from(intent, Optional.empty(), Optional.of(attempt));

        assertThat(dto.failureReason()).isNull();
    }

    @Test
    void recordAccessorsReturnConstructorValues() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(600);

        ExecutionDto dto = new ExecutionDto(
                id, UUID.randomUUID(), 3, UUID.randomUUID(),
                "key-abc", UUID.randomUUID(), ExecutionStatus.VALIDATED,
                now, now, expires, 5,
                "ext-1", "ACKNOWLEDGED",
                new BigDecimal("1.0"), new BigDecimal("50000"), new BigDecimal("10"),
                null
        );

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.tradePlanVersion()).isEqualTo(3);
        assertThat(dto.idempotencyKey()).isEqualTo("key-abc");
        assertThat(dto.status()).isEqualTo(ExecutionStatus.VALIDATED);
        assertThat(dto.version()).isEqualTo(5);
        assertThat(dto.brokerExternalOrderId()).isEqualTo("ext-1");
        assertThat(dto.brokerOrderStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(dto.filledQuantity()).isEqualByComparingTo(new BigDecimal("1.0"));
    }

    private ExecutionIntent createIntent(Instant now) {
        return ExecutionIntent.create(
                ExecutionIntentId.newId(),
                new TradePlanReference(UUID.randomUUID(), 1),
                new RiskApprovalReference(UUID.randomUUID(),
                        RiskApprovalReference.Decision.APPROVED, now),
                new IdempotencyKey("idem-key"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, new BigDecimal("0.1"), null),
                now,
                now.plusSeconds(3600)
        );
    }
}
