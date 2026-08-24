package com.hope.trading.trading_core.execution.api.dto;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference;
import com.hope.trading.trading_core.execution.domain.model.TradePlanReference;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
    }

    @Test
    void recordAccessorsReturnConstructorValues() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(600);

        ExecutionDto dto = new ExecutionDto(
                id, UUID.randomUUID(), 3, UUID.randomUUID(),
                "key-abc", UUID.randomUUID(), ExecutionStatus.VALIDATED,
                now, now, expires, 5
        );

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.tradePlanVersion()).isEqualTo(3);
        assertThat(dto.idempotencyKey()).isEqualTo("key-abc");
        assertThat(dto.status()).isEqualTo(ExecutionStatus.VALIDATED);
        assertThat(dto.version()).isEqualTo(5);
    }
}
